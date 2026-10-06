package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.AiState
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.SpeechClient
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.tags.ListEmpty
import io.github.nytka_app.ui.tags.listEmpty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Where a conversation's summary stands; the screen words it. */
enum class SummaryChip { Summarizing, Failed }

data class ConversationRow(
    val id: String,
    val timeRange: String,
    val length: String,
    /** The summary when there is one, else the transcript's first words. */
    val preview: String,
    /** The title, generated or set; null until the first run, when the row shows the time range instead. */
    val title: String? = null,
    /** Summarizing or failed; null otherwise, and always on a server before v0.2. */
    val chip: SummaryChip? = null,
    /** How many bookmarks the conversation holds; the row shows an icon when it is above zero. */
    val bookmarks: Int = 0,
    /** Sorted tag names; none from a server without the `tags` feature. */
    val tags: List<String> = emptyList(),
    /** The conversation is mostly media (a `mediaShare` of [MEDIA_SHARE] or more); the row shows a chip. */
    val media: Boolean = false,
)

/** From this share of a conversation's speech being media, the list calls the conversation media. */
const val MEDIA_SHARE = 0.8

data class DaySection(
    val title: String,
    val rows: List<ConversationRow>,
)

data class ConversationsUiState(
    val days: List<DaySection> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    /** The tag the list is filtered by; kept in saved state, so it survives rotation but not a launch. */
    val tag: String? = null,
    /** The list leaves out media conversations; kept in saved state like [tag]. */
    val hideMedia: Boolean = false,
) {
    val empty: ListEmpty get() = listEmpty(days.isEmpty(), loading, error != null, tag)
}

@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        private val api: ConversationsClient,
        private val status: StatusClient,
        private val clock: Clock,
        private val tags: TagsClient,
        private val info: InfoClient,
        private val speech: SpeechClient,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val items = mutableListOf<ConversationSummary>()
        private var nextBefore: String? = null
        private var loadJob: Job? = null

        /** Counts loads started, so a quiet refresh can tell that a newer answer has come in while it waited. */
        private var loadsStarted = 0
        private var tag: String? = savedState[TAG]
        private var hideMedia: Boolean = savedState[HIDE_MEDIA] ?: false

        /** Whether `/info` lists `speech-kind`; null until it has been read. */
        private var speechKind: Boolean? = null
        private val mutableState = MutableStateFlow(ConversationsUiState(tag = tag, hideMedia = hideMedia))
        val state: StateFlow<ConversationsUiState> = mutableState.asStateFlow()
        private val mutableNotice = MutableStateFlow<String?>(null)
        private val mutableTagFilter = MutableStateFlow(false)
        private val mutableMediaFilter = MutableStateFlow(false)

        /** The server lists the `tags` feature: the screen offers the tag action. */
        val tagFilter: StateFlow<Boolean> = mutableTagFilter.asStateFlow()

        /** The server lists the `speech-kind` feature: the screen offers **Hide media**. */
        val mediaFilter: StateFlow<Boolean> = mutableMediaFilter.asStateFlow()

        /** What the status card says about transcription on the server; null while nothing is wrong or known. */
        val notice: StateFlow<String?> = mutableNotice.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            load(reset = true)
            viewModelScope.launch { refreshNotice() }
            viewModelScope.launch { refreshTagFilter() }
        }

        /** A failed read of `/info` changes nothing: the action stays as it was. */
        private suspend fun refreshTagFilter() {
            (info.info() as? ApiResult.Ok)?.let {
                mutableTagFilter.value = it.value.has(ServerInfo.FEATURE_TAGS)
                speechKind = it.value.has(ServerInfo.FEATURE_SPEECH_KIND)
                mutableMediaFilter.value = speechKind == true
            }
        }

        /** Whether the server lists `speech-kind`; null when `/info` cannot be read. */
        private suspend fun speechKindListed(): Boolean? {
            speechKind?.let { return it }
            val listed = (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_SPEECH_KIND) ?: return null
            speechKind = listed
            return listed
        }

        /** Leaves media conversations out of the list and reads it again. A server without `speech-kind` keeps it. */
        fun hideMedia() {
            viewModelScope.launch {
                if (speechKindListed() != true) return@launch
                setHideMedia(true)
            }
        }

        fun showMedia() = setHideMedia(false)

        private fun setHideMedia(on: Boolean) {
            if (on == hideMedia) return
            hideMedia = on
            savedState[HIDE_MEDIA] = on
            items.clear()
            nextBefore = null
            mutableState.update { it.copy(days = emptyList(), hideMedia = on, endReached = false) }
            load(reset = true)
        }

        /**
         * Filters the list by [name], a tag taken from a chip, and reads it again from the start. A server without the
         * `tags` feature keeps the full list.
         */
        fun showTag(name: String) {
            viewModelScope.launch {
                if ((info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_TAGS) != true) return@launch
                setTag(name)
            }
        }

        fun clearTag() = setTag(null)

        private fun setTag(name: String?) {
            if (name == tag) return
            tag = name
            savedState[TAG] = name
            // The old list is not the new one's first page.
            items.clear()
            nextBefore = null
            mutableState.update { it.copy(days = emptyList(), tag = name, endReached = false) }
            load(reset = true)
        }

        private suspend fun page(before: String?): ApiResult<ConversationPage> {
            if (hideMedia) {
                when (speechKindListed()) {
                    true -> return speech.conversationsWithoutMedia(tag, before, PAGE_SIZE)
                    // A saved filter outlives the server's feature: the full list is the safe answer.
                    false -> {
                        hideMedia = false
                        savedState[HIDE_MEDIA] = false
                    }
                    null -> return ApiResult.Failure(FailureKind.Network, "The server did not answer.")
                }
            }
            val filter = tag
            return if (filter ==
                null
            ) {
                api.conversations(before, PAGE_SIZE)
            } else {
                tags.conversations(filter, before, PAGE_SIZE)
            }
        }

        fun loadMore() {
            val current = mutableState.value
            if (!current.endReached && !current.loading && loadJob?.isActive != true) load(reset = false)
        }

        /**
         * Keeps the list and the transcription notice fresh for as long as the caller lives: at once, then every
         * [REFRESH_MS]. The screen runs it while it is resumed, so it stops in the background and starts again, with a
         * refresh, on return.
         */
        suspend fun keepFresh() {
            while (true) {
                refreshQuietly()
                refreshNotice()
                delay(REFRESH_MS)
            }
        }

        /**
         * Brings in what is new without the pull-to-refresh spinner and without dropping the pages the user has
         * loaded. A failure changes nothing: the status card already says when the server is out of reach.
         */
        private suspend fun refreshQuietly() {
            // A load under way brings the same news.
            if (loadJob?.isActive == true) return
            val started = loadsStarted
            val page = (page(null) as? ApiResult.Ok)?.value ?: return
            // A load that started meanwhile has a newer answer than this one.
            if (loadsStarted != started) return
            val beyond = loadedBeyond(page)
            items.clear()
            items += page.items
            items += beyond
            // The page's cursor leads to the pages after it. With more loaded than it covers, the old one stays.
            if (beyond.isEmpty()) nextBefore = page.nextBefore
            mutableState.update { it.copy(days = group(items), error = null, endReached = nextBefore == null) }
        }

        /** A failed call says nothing about transcription, so the card keeps what it showed. */
        private suspend fun refreshNotice() {
            (status.status() as? ApiResult.Ok)?.let {
                mutableNotice.value = transcriptionNotice(it.value, clock.instant(), clock.zone)
            }
        }

        /** What the user has loaded past the fresh first [page]: the conversations older than its last one. */
        private fun loadedBeyond(page: ConversationPage): List<ConversationSummary> {
            val last = page.items.lastOrNull()
            // A page that is the whole list has nothing after it; anything else loaded was deleted elsewhere.
            if (last == null || page.nextBefore == null) return emptyList()
            return items.filter { it.isOlderThan(last) }
        }

        /** Drops a conversation deleted on its own screen, without reloading the list. */
        fun forget(id: String) {
            items.removeAll { it.id == id }
            mutableState.update { it.copy(days = group(items)) }
        }

        private fun load(reset: Boolean) {
            loadsStarted++
            // A refresh supersedes a page load in flight: its cursor belongs to the old list.
            if (reset) loadJob?.cancel()
            mutableState.update { it.copy(loading = true, refreshing = reset, error = null) }
            loadJob =
                viewModelScope.launch {
                    when (val result = page(if (reset) null else nextBefore)) {
                        is ApiResult.Ok -> {
                            if (reset) items.clear()
                            // Rows are keyed by id: a conversation that moved pages must not appear twice.
                            val known = items.mapTo(HashSet()) { it.id }
                            items += result.value.items.filter { it.id !in known }
                            nextBefore = result.value.nextBefore
                            mutableState.value =
                                ConversationsUiState(
                                    days = group(items),
                                    endReached = nextBefore == null,
                                    tag = tag,
                                    hideMedia = hideMedia,
                                )
                        }

                        is ApiResult.Failure ->
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    refreshing = false,
                                    error = result.message,
                                )
                            }
                    }
                }
        }

        private fun group(items: List<ConversationSummary>): List<DaySection> {
            val zone = clock.zone
            val today = LocalDate.now(clock)
            return items
                .groupBy { Formatting.instant(it.startedAt).atZone(zone).toLocalDate() }
                .map { (date, rows) ->
                    DaySection(
                        Formatting.dayTitle(date, today),
                        rows.map {
                            val start = Formatting.instant(it.startedAt)
                            val end = Formatting.instant(it.endedAt)
                            ConversationRow(
                                it.id,
                                Formatting.timeRange(start, end, zone),
                                Formatting.length(start, end),
                                it.summary?.takeIf(String::isNotBlank) ?: it.preview,
                                it.title?.takeIf(String::isNotBlank),
                                aiChip(it.aiStatus),
                                it.bookmarks,
                                it.tags,
                                it.mediaShare >= MEDIA_SHARE,
                            )
                        },
                    )
                }
        }

        private fun aiChip(aiStatus: String): SummaryChip? =
            when (aiStatus) {
                AiState.PENDING -> SummaryChip.Summarizing
                AiState.FAILED -> SummaryChip.Failed
                else -> null
            }

        /** Newest first, as the server lists them: is this one later in that order than [other]? */
        private fun ConversationSummary.isOlderThan(other: ConversationSummary): Boolean {
            val order = Formatting.instant(startedAt).compareTo(Formatting.instant(other.startedAt))
            return order < 0 || (order == 0 && id < other.id)
        }

        companion object {
            /** How often the list refreshes itself while the screen is shown. */
            const val REFRESH_MS = 30_000L

            private const val PAGE_SIZE = 30
            private const val TAG = "tag"
            private const val HIDE_MEDIA = "hideMedia"
        }
    }
