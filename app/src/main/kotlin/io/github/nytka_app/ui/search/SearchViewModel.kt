package io.github.nytka_app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Hit
import io.github.nytka_app.core.api.SearchClient
import io.github.nytka_app.ui.memories.MemoryFormatting
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class SearchFilter(
    val label: String,
    val kinds: Set<String>,
) {
    All("All", emptySet()),
    Conversations("Conversations", setOf("conversation")),
    Memories("Memories", setOf("memory")),
}

/** A run of a snippet: [bold] where the server wrapped it in `<mark>`. */
data class SnippetSpan(
    val text: String,
    val bold: Boolean,
)

/**
 * [title] is null for a memory, whose text is the snippet. [openId] is the conversation a tap opens, null for a
 * memory without a source. [personId] is set for a person hit, which opens that person instead.
 */
data class HitRow(
    val key: String,
    val title: String?,
    val snippet: List<SnippetSpan>,
    val date: String,
    val openId: String?,
    val personId: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    val rows: List<HitRow> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = true,
) {
    /** A search finished with no hits and no failure. */
    val nothingFound: Boolean get() = searched && !searching && rows.isEmpty() && error == null
}

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val api: SearchClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val hits = mutableListOf<Hit>()
        private var nextOffset: Int? = null
        private var job: Job? = null
        private val mutableState = MutableStateFlow(SearchUiState())
        val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

        fun setQuery(query: String) {
            mutableState.update { it.copy(query = query) }
            restart(debounce = true)
        }

        fun setFilter(filter: SearchFilter) {
            if (filter == mutableState.value.filter) return
            mutableState.update { it.copy(filter = filter) }
            restart(debounce = false)
        }

        fun retry() = restart(debounce = false)

        fun loadMore() {
            val offset = nextOffset ?: return
            if (job?.isActive == true) return
            run(offset)
        }

        private fun restart(debounce: Boolean) {
            job?.cancel()
            hits.clear()
            nextOffset = null
            val current = mutableState.value
            if (!searchable(current.query)) {
                mutableState.update { it.copy(rows = emptyList(), searching = false, searched = false, error = null) }
                return
            }
            mutableState.update { it.copy(rows = emptyList(), searching = true, error = null, endReached = true) }
            run(0, if (debounce) DEBOUNCE_MS else 0)
        }

        private fun run(
            offset: Int,
            wait: Long = 0,
        ) {
            val current = mutableState.value
            job =
                viewModelScope.launch {
                    if (wait > 0) delay(wait)
                    mutableState.update { it.copy(searching = true) }
                    when (val result = api.search(current.query.trim(), current.filter.kinds, PAGE_SIZE, offset)) {
                        is ApiResult.Ok -> {
                            hits += result.value.items
                            nextOffset = result.value.nextOffset
                            mutableState.update {
                                it.copy(
                                    rows = hits.map(::row),
                                    searching = false,
                                    searched = true,
                                    endReached = nextOffset == null,
                                )
                            }
                        }

                        is ApiResult.Failure ->
                            mutableState.update {
                                it.copy(searching = false, searched = true, error = result.notice())
                            }
                    }
                }
        }

        private fun row(hit: Hit): HitRow {
            if (hit.kind == "person") {
                // A person's snippet may be empty; the name then stands as the text.
                val spans = markSpans(hit.snippet).ifEmpty { listOf(SnippetSpan(hit.title.orEmpty(), bold = true)) }
                return HitRow(
                    key = "person-${hit.id}",
                    title = hit.title.takeIf { hit.snippet.isNotBlank() },
                    snippet = spans,
                    date = MemoryFormatting.day(hit.at, clock).orEmpty(),
                    openId = null,
                    personId = hit.id,
                )
            }
            val isMemory = hit.kind == "memory"
            return HitRow(
                key = "${hit.kind}-${hit.id}",
                title = hit.title,
                snippet = markSpans(hit.snippet),
                date = MemoryFormatting.day(hit.at, clock).orEmpty(),
                openId = if (isMemory) hit.conversationId else hit.conversationId ?: hit.id,
            )
        }

        companion object {
            const val DEBOUNCE_MS = 300L
            private const val PAGE_SIZE = 20

            /** A query needs at least one letter or digit. */
            fun searchable(query: String) = query.any { it.isLetterOrDigit() }

            /** Splits a snippet at its `<mark>` tags; the rest of the text is plain, tags and all. */
            fun markSpans(snippet: String): List<SnippetSpan> {
                val spans = mutableListOf<SnippetSpan>()
                var rest = snippet
                while (rest.isNotEmpty()) {
                    val start = rest.indexOf(OPEN)
                    val end = if (start < 0) -1 else rest.indexOf(CLOSE, start + OPEN.length)
                    if (start < 0 || end < 0) {
                        spans += SnippetSpan(rest, bold = false)
                        break
                    }
                    if (start > 0) spans += SnippetSpan(rest.substring(0, start), bold = false)
                    spans += SnippetSpan(rest.substring(start + OPEN.length, end), bold = true)
                    rest = rest.substring(end + CLOSE.length)
                }
                return spans
            }

            private const val OPEN = "<mark>"
            private const val CLOSE = "</mark>"
        }
    }
