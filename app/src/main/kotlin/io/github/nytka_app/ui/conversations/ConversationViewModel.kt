package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.AiState
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.BookmarksClient
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.TasksClient
import io.github.nytka_app.ui.ITEM_GONE
import io.github.nytka_app.ui.itemNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * One segment. [speaker] is what shows above a run: "Me" for the wearer, else the name given to the voice, else the
 * label the transcription gave it. [showSpeaker] is true where a run by one speaker begins, and [speakerColor] picks
 * that speaker's color by first appearance. [voiceId] is set where the voice can be named.
 */
data class Paragraph(
    val time: String,
    val text: String,
    val speaker: String? = null,
    val showSpeaker: Boolean = false,
    val speakerColor: Int = 0,
    val voiceId: String? = null,
    /** The bookmarks whose time is nearest this paragraph. */
    val bookmarks: List<BookmarkMark> = emptyList(),
)

data class BookmarkMark(
    val id: String,
    val note: String? = null,
)

data class TaskLine(
    val id: String,
    val text: String,
    val done: Boolean,
)

data class ConversationUiState(
    /** The title, generated or set; the day until the first summary. */
    val title: String = "",
    /** The title a person set or the model made, without the day fallback, to prefill Rename. */
    val currentTitle: String? = null,
    val timeRange: String = "",
    val length: String = "",
    val summary: String? = null,
    /** "Summarizing" or "Summary failed"; null otherwise, and on a server before v0.2. */
    val chip: String? = null,
    val tasks: List<TaskLine> = emptyList(),
    val paragraphs: List<Paragraph> = emptyList(),
    /** Bookmarks of a conversation with no transcript, so there is no paragraph to mark. */
    val looseBookmarks: List<BookmarkMark> = emptyList(),
    val open: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
    val raw: String? = null,
)

@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: ConversationsClient,
        private val tasks: TasksClient,
        private val bookmarks: BookmarksClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"]) { "The conversation route carries an id." }
        private val mutableState = MutableStateFlow(ConversationUiState())
        val state: StateFlow<ConversationUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                when (val result = api.conversation(id)) {
                    is ApiResult.Ok -> mutableState.value = show(result.value)
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                error =
                                    if (result.kind == FailureKind.NotFound) ITEM_GONE else result.message,
                            )
                        }
                }
            }
        }

        /**
         * While the summary is being made, looks again every [POLL_MS] until it is there. The screen runs it while it
         * is resumed; it returns at once when nothing is pending.
         */
        suspend fun keepFresh() {
            while (mutableState.value.chip == PENDING_CHIP) {
                delay(POLL_MS)
                (api.conversation(id) as? ApiResult.Ok)?.let { mutableState.value = show(it.value) }
            }
        }

        fun rename(title: String) {
            // An empty title restores the generated one; the server allows 120 characters.
            val wanted = title.trim().take(MAX_TITLE).ifEmpty { null }
            viewModelScope.launch {
                when (val result = api.renameConversation(id, wanted)) {
                    is ApiResult.Ok -> mutableState.value = show(result.value)
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.itemNotice()) }
                }
            }
        }

        /** Names the voice; the transcript is read again, so every line of that voice shows the name. */
        fun nameVoice(
            speakerId: String,
            name: String,
        ) {
            val wanted = name.trim().take(MAX_NAME)
            if (wanted.isEmpty()) return
            viewModelScope.launch {
                when (val result = api.nameVoice(speakerId, wanted)) {
                    is ApiResult.Ok ->
                        (api.conversation(id) as? ApiResult.Ok)?.let {
                            mutableState.value =
                                show(it.value)
                        }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.itemNotice()) }
                }
            }
        }

        fun regenerate() {
            if (mutableState.value.open) return
            viewModelScope.launch {
                when (val result = api.enrichConversation(id)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(chip = PENDING_CHIP, error = null) }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                error =
                                    if (result.kind == FailureKind.Conflict) {
                                        "No summary can be made now: the conversation is still open, or the server " +
                                            "has no language model set up."
                                    } else {
                                        result.itemNotice()
                                    },
                            )
                        }
                }
            }
        }

        fun setTaskDone(
            taskId: String,
            done: Boolean,
        ) {
            markTask(taskId, done)
            viewModelScope.launch {
                if (tasks.setDone(taskId, done) is ApiResult.Failure) {
                    markTask(taskId, !done)
                    mutableState.update { it.copy(error = "The task could not be saved.") }
                }
            }
        }

        private fun markTask(
            taskId: String,
            done: Boolean,
        ) = mutableState.update { state ->
            state.copy(tasks = state.tasks.map { if (it.id == taskId) it.copy(done = done) else it })
        }

        /** Saves the note of a bookmark; an empty one clears it. The server allows 200 characters. */
        fun setBookmarkNote(
            bookmarkId: String,
            note: String,
        ) {
            val wanted = note.trim().take(MAX_BOOKMARK_NOTE).ifEmpty { null }
            viewModelScope.launch {
                when (val result = bookmarks.setBookmarkNote(bookmarkId, wanted)) {
                    is ApiResult.Ok -> mutableState.update { it.withNote(bookmarkId, wanted) }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.itemNotice()) }
                }
            }
        }

        private fun ConversationUiState.withNote(
            bookmarkId: String,
            note: String?,
        ): ConversationUiState {
            fun List<BookmarkMark>.set() = map { if (it.id == bookmarkId) it.copy(note = note) else it }
            return copy(
                paragraphs = paragraphs.map { it.copy(bookmarks = it.bookmarks.set()) },
                looseBookmarks = looseBookmarks.set(),
            )
        }

        fun delete() {
            viewModelScope.launch {
                when (val result = api.deleteConversation(id)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(deleted = true) }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.message) }
                }
            }
        }

        fun loadRaw() {
            viewModelScope.launch {
                when (val result = api.transcriptionsJson(id)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(raw = result.value) }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.message) }
                }
            }
        }

        private fun show(detail: ConversationDetail): ConversationUiState {
            val zone = clock.zone
            val start = Formatting.instant(detail.startedAt)
            val end = Formatting.instant(detail.endedAt)
            val title = detail.title?.takeIf(String::isNotBlank)
            val colors = LinkedHashMap<String, Int>()
            var previous: String? = null
            val paragraphs =
                detail.segments.map {
                    val speaker =
                        if (it.isUser == true) ME else (it.personName ?: it.speaker)?.takeIf(String::isNotBlank)
                    val color = speaker?.let { name -> colors.getOrPut(name) { colors.size % SPEAKER_COLORS } } ?: 0
                    Paragraph(
                        Formatting.clock(Formatting.instant(it.startedAt), zone),
                        it.text,
                        speaker,
                        showSpeaker = speaker != null && speaker != previous,
                        speakerColor = color,
                        voiceId = it.speakerId.takeIf { _ -> it.isUser != true },
                    ).also { previous = speaker }
                }
            val marks = place(detail)
            return ConversationUiState(
                title = title ?: Formatting.dayTitle(start.atZone(zone).toLocalDate(), LocalDate.now(clock)),
                currentTitle = title,
                timeRange = Formatting.timeRange(start, end, zone),
                length = Formatting.length(start, end),
                summary = detail.summary?.takeIf(String::isNotBlank),
                chip =
                    when (detail.aiStatus) {
                        AiState.PENDING -> PENDING_CHIP
                        AiState.FAILED -> "Summary failed"
                        else -> null
                    },
                tasks = detail.tasks.map { TaskLine(it.id, it.text, it.done) },
                paragraphs = paragraphs.mapIndexed { i, p -> p.copy(bookmarks = marks[i].orEmpty()) },
                looseBookmarks =
                    if (paragraphs.isEmpty()) detail.bookmarks.map { BookmarkMark(it.id, it.note) } else emptyList(),
                open = detail.status == "open",
                loading = false,
            )
        }

        /**
         * The bookmarks of each segment, by index: a bookmark goes to the segment whose span is nearest its time, the
         * earlier one on a tie. A bookmark inside a segment is at distance zero.
         */
        private fun place(detail: ConversationDetail): Map<Int, List<BookmarkMark>> {
            val spans = detail.segments.map { Formatting.instant(it.startedAt) to Formatting.instant(it.endedAt) }
            if (spans.isEmpty()) return emptyMap()
            return detail.bookmarks
                .sortedBy { Formatting.instant(it.at) }
                .groupBy({ nearest(spans, Formatting.instant(it.at)) }) { BookmarkMark(it.id, it.note) }
        }

        private fun nearest(
            spans: List<Pair<Instant, Instant>>,
            at: Instant,
        ): Int =
            spans.indices.minBy { i ->
                val (start, end) = spans[i]
                when {
                    at < start -> Duration.between(at, start)
                    at > end -> Duration.between(end, at)
                    else -> Duration.ZERO
                }
            }

        companion object {
            /** How often a summary in the making is looked for. */
            const val POLL_MS = 5_000L

            /** The palette has six colors; a seventh speaker starts over. */
            const val SPEAKER_COLORS = 6

            private const val MAX_TITLE = 120
            private const val MAX_NAME = 80
            const val MAX_BOOKMARK_NOTE = 200
            private const val ME = "Me"
            private const val PENDING_CHIP = "Summarizing"
        }
    }
