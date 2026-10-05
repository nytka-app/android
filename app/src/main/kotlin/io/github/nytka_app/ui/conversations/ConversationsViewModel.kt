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
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.core.api.TagsClient
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

data class ConversationRow(
    val id: String,
    val timeRange: String,
    val length: String,
    /** The summary when there is one, else the transcript's first words. */
    val preview: String,
    /** The title, generated or set; null until the first run, when the row shows the time range instead. */
    val title: String? = null,
    /** "Summarizing" or "Summary failed"; null otherwise, and always on a server before v0.2. */
    val chip: String? = null,
    /** How many bookmarks the conversation holds; the row shows an icon when it is above zero. */
    val bookmarks: Int = 0,
)

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
)

@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        private val api: ConversationsClient,
        private val status: StatusClient,
        private val clock: Clock,
        private val tags: TagsClient,
        private val info: InfoClient,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val items = mutableListOf<ConversationSummary>()
        private var nextBefore: String? = null
        private var loadJob: Job? = null

        /** Counts loads started, so a quiet refresh can tell that a newer answer has come in while it waited. */
        private var loadsStarted = 0
        private var tag: String? = savedState[TAG]
        private val mutableState = MutableStateFlow(ConversationsUiState(tag = tag))
        val state: StateFlow<ConversationsUiState> = mutableState.asStateFlow()
        private val mutableNotice = MutableStateFlow<String?>(null)

        /** What the status card says about transcription on the server; null while nothing is wrong or known. */
        val notice: StateFlow<String?> = mutableNotice.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            load(reset = true)
            viewModelScope.launch { refreshNotice() }
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
                                ConversationsUiState(days = group(items), endReached = nextBefore == null, tag = tag)
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
                            )
                        },
                    )
                }
        }

        private fun aiChip(aiStatus: String): String? =
            when (aiStatus) {
                AiState.PENDING -> "Summarizing"
                AiState.FAILED -> "Summary failed"
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
        }
    }
