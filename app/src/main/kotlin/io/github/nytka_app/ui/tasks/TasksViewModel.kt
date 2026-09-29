package io.github.nytka_app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.TasksClient
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.itemNotice
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TaskRow(
    val id: String,
    val text: String,
    val done: Boolean,
    val conversationId: String,
    /** The conversation's title, else its day; empty on a server that sends neither. */
    val source: String,
)

data class TasksUiState(
    val open: List<TaskRow> = emptyList(),
    val completed: List<TaskRow> = emptyList(),
    val completedShown: Boolean = false,
    val openEndReached: Boolean = false,
    val completedEndReached: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Why the last load or change failed; a v0.1 server says it needs an update. */
    val error: String? = null,
)

@HiltViewModel
class TasksViewModel
    @Inject
    constructor(
        private val api: TasksClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TasksUiState())
        val state: StateFlow<TasksUiState> = mutableState.asStateFlow()
        private var openCursor: String? = null
        private var completedCursor: String? = null
        private var loadingMore = false

        init {
            refresh()
        }

        fun refresh() {
            mutableState.update { it.copy(refreshing = true, error = null) }
            viewModelScope.launch {
                when (val result = api.tasks(done = false, before = null, limit = OPEN_PAGE)) {
                    is ApiResult.Ok -> {
                        openCursor = result.value.nextBefore
                        mutableState.update {
                            it.copy(
                                open = result.value.items.map(::row),
                                openEndReached = openCursor == null,
                                loading = false,
                                refreshing = false,
                            )
                        }
                        if (mutableState.value.completedShown) loadCompleted(reset = true)
                    }
                    is ApiResult.Failure ->
                        mutableState.update { it.copy(loading = false, refreshing = false, error = result.notice()) }
                }
            }
        }

        fun loadMoreOpen() {
            val cursor = openCursor ?: return
            if (loadingMore) return
            loadingMore = true
            viewModelScope.launch {
                when (val result = api.tasks(done = false, before = cursor, limit = OPEN_PAGE)) {
                    is ApiResult.Ok -> {
                        openCursor = result.value.nextBefore
                        mutableState.update {
                            it.copy(
                                open =
                                    it.open +
                                        result.value.items.map(::row).filter { new ->
                                            it.open.none { old ->
                                                old.id ==
                                                    new.id
                                            }
                                        },
                                openEndReached = openCursor == null,
                            )
                        }
                    }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.notice()) }
                }
                loadingMore = false
            }
        }

        /** Done tasks sit below, collapsed; the first look loads them, 30 at a time. */
        fun toggleCompleted() {
            val shown = !mutableState.value.completedShown
            mutableState.update { it.copy(completedShown = shown) }
            if (shown && mutableState.value.completed.isEmpty()) loadCompleted(reset = true)
        }

        fun loadMoreCompleted() {
            if (completedCursor != null) loadCompleted(reset = false)
        }

        private fun loadCompleted(reset: Boolean) {
            viewModelScope.launch {
                when (
                    val result =
                        api.tasks(
                            done = true,
                            before = if (reset) null else completedCursor,
                            limit = DONE_PAGE,
                        )
                ) {
                    is ApiResult.Ok -> {
                        completedCursor = result.value.nextBefore
                        val rows = result.value.items.map(::row)
                        mutableState.update {
                            it.copy(
                                completed =
                                    if (reset) {
                                        rows
                                    } else {
                                        it.completed +
                                            rows.filter { new -> it.completed.none { old -> old.id == new.id } }
                                    },
                                completedEndReached = completedCursor == null,
                            )
                        }
                    }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.notice()) }
                }
            }
        }

        /** Ticks or unticks [id] at once and tells the server; a refusal puts it back and reloads. */
        fun setDone(
            id: String,
            done: Boolean,
        ) {
            mutableState.update { state ->
                val task = (state.open + state.completed).firstOrNull { it.id == id } ?: return@update state
                val moved = task.copy(done = done)
                state.copy(
                    open =
                        if (done) {
                            state.open.filter {
                                it.id != id
                            }
                        } else {
                            (state.open + moved).sortedByDescending { it.id }
                        },
                    completed =
                        if (done) {
                            (state.completed + moved).sortedByDescending { it.id }
                        } else {
                            state.completed.filter { it.id != id }
                        },
                )
            }
            viewModelScope.launch {
                if (api.setDone(id, done) is ApiResult.Failure) {
                    mutableState.update { it.copy(error = "The task could not be saved.") }
                    refresh()
                }
            }
        }

        fun edit(
            id: String,
            text: String,
        ) {
            val wanted = text.trim().take(MAX_TEXT)
            if (wanted.isEmpty()) return
            viewModelScope.launch {
                when (val result = api.editText(id, wanted)) {
                    is ApiResult.Ok ->
                        mutableState.update { state ->
                            state.copy(
                                open = state.open.map { if (it.id == id) it.copy(text = result.value.text) else it },
                                completed =
                                    state.completed.map {
                                        if (it.id ==
                                            id
                                        ) {
                                            it.copy(text = result.value.text)
                                        } else {
                                            it
                                        }
                                    },
                                error = null,
                            )
                        }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.itemNotice()) }
                }
            }
        }

        fun delete(id: String) {
            viewModelScope.launch {
                when (val result = api.deleteTask(id)) {
                    is ApiResult.Ok ->
                        mutableState.update { state ->
                            state.copy(
                                open = state.open.filter { it.id != id },
                                completed = state.completed.filter { it.id != id },
                                error = null,
                            )
                        }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.itemNotice()) }
                }
            }
        }

        private fun row(task: NytkaTask): TaskRow {
            val day =
                task.conversationStartedAt
                    ?.let(Formatting::parse)
                    ?.let { Formatting.dayTitle(it.atZone(clock.zone).toLocalDate(), LocalDate.now(clock)) }
            val source = listOfNotNull(task.conversationTitle?.takeIf(String::isNotBlank), day).joinToString(" · ")
            return TaskRow(task.id, task.text, task.done, task.conversationId, source)
        }

        private companion object {
            const val OPEN_PAGE = 50
            const val DONE_PAGE = 30
            const val MAX_TEXT = 200
        }
    }
