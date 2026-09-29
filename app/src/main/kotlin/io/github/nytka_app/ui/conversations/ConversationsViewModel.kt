package io.github.nytka_app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.ConversationsClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ConversationRow(
    val id: String,
    val timeRange: String,
    val length: String,
    val preview: String,
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
)

@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        private val api: ConversationsClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val items = mutableListOf<ConversationSummary>()
        private var nextBefore: String? = null
        private var loadJob: Job? = null
        private val mutableState = MutableStateFlow(ConversationsUiState())
        val state: StateFlow<ConversationsUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() = load(reset = true)

        fun loadMore() {
            val current = mutableState.value
            if (!current.endReached && !current.loading && loadJob?.isActive != true) load(reset = false)
        }

        /** Drops a conversation deleted on its own screen, without reloading the list. */
        fun forget(id: String) {
            items.removeAll { it.id == id }
            mutableState.update { it.copy(days = group(items)) }
        }

        private fun load(reset: Boolean) {
            // A refresh supersedes a page load in flight: its cursor belongs to the old list.
            if (reset) loadJob?.cancel()
            mutableState.update { it.copy(loading = true, refreshing = reset, error = null) }
            loadJob =
                viewModelScope.launch {
                    when (val result = api.conversations(if (reset) null else nextBefore, PAGE_SIZE)) {
                        is ApiResult.Ok -> {
                            if (reset) items.clear()
                            // Rows are keyed by id: a conversation that moved pages must not appear twice.
                            val known = items.mapTo(HashSet()) { it.id }
                            items += result.value.items.filter { it.id !in known }
                            nextBefore = result.value.nextBefore
                            mutableState.value =
                                ConversationsUiState(days = group(items), endReached = nextBefore == null)
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
                .groupBy { Instant.parse(it.startedAt).atZone(zone).toLocalDate() }
                .map { (date, rows) ->
                    DaySection(
                        Formatting.dayTitle(date, today),
                        rows.map {
                            val start = Instant.parse(it.startedAt)
                            val end = Instant.parse(it.endedAt)
                            ConversationRow(
                                it.id,
                                Formatting.timeRange(start, end, zone),
                                Formatting.length(start, end),
                                it.preview,
                            )
                        },
                    )
                }
        }

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
