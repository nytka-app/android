package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.AiState
import io.github.nytka_app.core.api.ApiResult
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * One segment. [speaker] is the label the transcription gave it; [showSpeaker] is true where a run by one speaker
 * begins, and [speakerColor] picks that speaker's color by first appearance.
 */
data class Paragraph(
    val time: String,
    val text: String,
    val speaker: String? = null,
    val showSpeaker: Boolean = false,
    val speakerColor: Int = 0,
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
                    val speaker = it.speaker?.takeIf(String::isNotBlank)
                    val color = speaker?.let { name -> colors.getOrPut(name) { colors.size % SPEAKER_COLORS } } ?: 0
                    Paragraph(
                        Formatting.clock(Formatting.instant(it.startedAt), zone),
                        it.text,
                        speaker,
                        showSpeaker = speaker != null && speaker != previous,
                        speakerColor = color,
                    ).also { previous = speaker }
                }
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
                paragraphs = paragraphs,
                open = detail.status == "open",
                loading = false,
            )
        }

        companion object {
            /** How often a summary in the making is looked for. */
            const val POLL_MS = 5_000L

            /** The palette has six colors; a seventh speaker starts over. */
            const val SPEAKER_COLORS = 6

            private const val MAX_TITLE = 120
            private const val PENDING_CHIP = "Summarizing"
        }
    }
