package io.github.nytka_app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.Memory
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** A memory as the row shows it: [source] is the conversation's title and day, when there is one. */
data class MemoryRow(
    val id: String,
    val text: String,
    val source: String?,
    val conversationId: String?,
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
    val endReached: Boolean = false,
    val editor: MemoryEditor? = null,
)

@HiltViewModel
class MemoriesViewModel
    @Inject
    constructor(
        private val api: MemoriesClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val items = mutableListOf<Memory>()
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
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(editor = editor.copy(saving = false, error = editorError(result)))
                        }
                }
            }
        }

        fun delete(id: String) {
            viewModelScope.launch {
                when (val result = api.deleteMemory(id)) {
                    // A memory that is already gone is what the user wanted.
                    is ApiResult.Ok -> forget(id)
                    is ApiResult.Failure ->
                        if (result.kind == FailureKind.NotFound) forget(id) else fail(result.notice())
                }
            }
        }

        private fun forget(id: String) {
            items.removeAll { it.id == id }
            mutableState.update { it.copy(rows = rows(), error = null) }
        }

        private fun fail(message: String) = mutableState.update { it.copy(error = message) }

        private fun editorError(failure: ApiResult.Failure): String =
            when (failure.kind) {
                FailureKind.Conflict -> "This memory already exists."
                FailureKind.Invalid ->
                    failure.errors.values
                        .flatten()
                        .firstOrNull() ?: failure.message
                else -> failure.notice()
            }

        private fun load(reset: Boolean) {
            if (reset) loadJob?.cancel()
            mutableState.update { it.copy(loading = true, refreshing = reset, error = null) }
            loadJob =
                viewModelScope.launch {
                    when (val result = api.memories(if (reset) null else nextBefore, PAGE_SIZE)) {
                        is ApiResult.Ok -> {
                            if (reset) items.clear()
                            val known = items.mapTo(HashSet()) { it.id }
                            items += result.value.items.filter { it.id !in known }
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
                                it.copy(loading = false, refreshing = false, error = result.notice())
                            }
                    }
                }
        }

        private fun rows(): List<MemoryRow> =
            items.map { memory ->
                MemoryRow(memory.id, memory.text, sourceLine(memory), memory.conversationId)
            }

        private fun sourceLine(memory: Memory): String? {
            val title = memory.conversationTitle
            val started = memory.conversationStartedAt?.let { Instant.parse(it) }
            val day = started?.let { MemoryFormatting.day(it, clock) }
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
        }
    }
