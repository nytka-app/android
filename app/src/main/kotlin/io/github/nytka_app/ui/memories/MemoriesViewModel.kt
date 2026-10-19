package io.github.nytka_app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.Memory
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.itemNotice
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.unreachable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** How long ago a memory was learned, as the Memories tab groups them. */
enum class MemoryGroup { Today, ThisWeek, ThisMonth, Earlier }

/** A memory as the row shows it: [source] is the conversation's title and day, when there is one. */
data class MemoryRow(
    val id: String,
    val text: String,
    val source: String?,
    val conversationId: String?,
    val group: MemoryGroup = MemoryGroup.Earlier,
)

/** The add or edit dialog: [target] is the memory being edited, null when adding. [error] is the server's refusal. */
data class MemoryEditor(
    val target: MemoryRow? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

data class MemoriesUiState(
    val rows: List<MemoryRow> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val endReached: Boolean = false,
    val editor: MemoryEditor? = null,
    /** The memory forgotten last, until the snackbar's Undo runs out; the server is asked only then. */
    val forgotten: String? = null,
)

@HiltViewModel
class MemoriesViewModel
    @Inject
    constructor(
        private val api: MemoriesClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val items = mutableListOf<Memory>()

        /** Forgotten, not yet sent to the server: the memory and the place it had in the list. */
        private val pending = LinkedHashMap<String, Pair<Int, Memory>>()
        private var nextBefore: String? = null
        private var loadJob: Job? = null
        private val mutableState = MutableStateFlow(MemoriesUiState())
        val state: StateFlow<MemoriesUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() = load(reset = true)

        fun loadMore() {
            val current = mutableState.value
            if (!current.endReached && !current.loading && loadJob?.isActive != true) load(reset = false)
        }

        fun startAdd() = mutableState.update { it.copy(editor = MemoryEditor()) }

        fun startEdit(id: String) {
            val row = mutableState.value.rows.firstOrNull { it.id == id } ?: return
            mutableState.update { it.copy(editor = MemoryEditor(target = row)) }
        }

        fun dismissEditor() = mutableState.update { it.copy(editor = null) }

        /** Adds or, while editing, rewrites. A refusal stays in the dialog so the text is not lost. */
        fun save(text: String) {
            val editor = mutableState.value.editor ?: return
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH || editor.saving) return
            mutableState.update { it.copy(editor = editor.copy(saving = true, error = null)) }
            viewModelScope.launch {
                val result =
                    editor.target?.let { api.editMemory(it.id, trimmed) } ?: api.addMemory(trimmed)
                when (result) {
                    is ApiResult.Ok -> {
                        val saved = result.value
                        val at = items.indexOfFirst { it.id == saved.id }
                        if (at >= 0) items[at] = saved else items.add(0, saved)
                        mutableState.update { it.copy(rows = rows(), editor = null) }
                    }

                    is ApiResult.Failure -> {
                        val gone = editor.target != null && result.kind == FailureKind.NotFound
                        mutableState.update {
                            it.copy(
                                editor =
                                    editor.copy(
                                        saving = false,
                                        error =
                                            editorError(
                                                result,
                                                editor.target != null,
                                            ),
                                    ),
                            )
                        }
                        if (gone) refresh()
                    }
                }
            }
        }

        /**
         * Takes the memory off the list at once but asks the server only in [commitForget], so Undo costs nothing.
         * A second forget commits the first. Closing the app in between leaves the memory where it was.
         */
        fun forget(id: String) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            mutableState.value.forgotten?.let(::commitForget)
            pending[id] = index to items.removeAt(index)
            mutableState.update { it.copy(rows = rows(), forgotten = id) }
        }

        fun undoForget(id: String) {
            val (index, memory) = pending.remove(id) ?: return
            items.add(index.coerceAtMost(items.size), memory)
            mutableState.update { it.copy(rows = rows(), forgotten = it.forgotten.takeIf { f -> f != id }) }
        }

        fun commitForget(id: String) {
            val (index, memory) = pending.remove(id) ?: return
            mutableState.update { it.copy(forgotten = it.forgotten.takeIf { f -> f != id }) }
            viewModelScope.launch {
                val result = api.deleteMemory(id)
                // A memory that is already gone is what the user wanted.
                if (result is ApiResult.Failure && result.kind != FailureKind.NotFound) {
                    items.add(index.coerceAtMost(items.size), memory)
                    mutableState.update { it.copy(rows = rows(), error = result.notice()) }
                }
            }
        }

        private fun fail(message: String) = mutableState.update { it.copy(error = message) }

        private fun editorError(
            failure: ApiResult.Failure,
            editing: Boolean,
        ): String =
            when (failure.kind) {
                FailureKind.Conflict -> "This memory already exists."
                FailureKind.Invalid ->
                    failure.errors.values
                        .flatten()
                        .firstOrNull() ?: failure.message

                else -> if (editing) failure.itemNotice() else failure.notice()
            }

        private fun load(reset: Boolean) {
            if (reset) loadJob?.cancel()
            mutableState.update { it.copy(loading = true, refreshing = reset, error = null, offline = false) }
            loadJob =
                viewModelScope.launch {
                    when (val result = api.memories(if (reset) null else nextBefore, PAGE_SIZE)) {
                        is ApiResult.Ok -> {
                            if (reset) items.clear()
                            val known = items.mapTo(HashSet()) { it.id }
                            items += result.value.items.filter { it.id !in known && it.id !in pending }
                            nextBefore = result.value.nextBefore
                            mutableState.update {
                                it.copy(
                                    rows = rows(),
                                    loading = false,
                                    refreshing = false,
                                    endReached = nextBefore == null,
                                )
                            }
                        }

                        is ApiResult.Failure ->
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    refreshing = false,
                                    error = result.notice(),
                                    offline = result.kind.unreachable,
                                )
                            }
                    }
                }
        }

        private fun rows(): List<MemoryRow> =
            items.map { memory ->
                MemoryRow(memory.id, memory.text, sourceLine(memory), memory.conversationId, group(memory))
            }

        private fun group(memory: Memory): MemoryGroup {
            val learned = Formatting.parse(memory.createdAt) ?: return MemoryGroup.Earlier
            val days = ChronoUnit.DAYS.between(learned.atZone(clock.zone).toLocalDate(), LocalDate.now(clock))
            return when {
                days < 1 -> MemoryGroup.Today
                days < WEEK -> MemoryGroup.ThisWeek
                days < MONTH -> MemoryGroup.ThisMonth
                else -> MemoryGroup.Earlier
            }
        }

        private fun sourceLine(memory: Memory): String? {
            val title = memory.conversationTitle
            val day = MemoryFormatting.day(memory.conversationStartedAt, clock)
            return when {
                title != null && day != null -> "$title · $day"
                title != null -> title
                memory.conversationId != null && day != null -> day
                else -> null
            }
        }

        companion object {
            const val MAX_LENGTH = 300
            private const val PAGE_SIZE = 50
            private const val WEEK = 7
            private const val MONTH = 30
        }
    }
