package io.github.nytka_app.ui.search

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Hit
import io.github.nytka_app.core.api.SearchClient
import io.github.nytka_app.core.api.SearchPage
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Call(
        val query: String,
        val kinds: Set<String>,
        val offset: Int,
    )

    private class FakeSearch : SearchClient {
        val calls = mutableListOf<Call>()
        var pages = ArrayDeque<ApiResult<SearchPage>>()

        override suspend fun search(
            query: String,
            kinds: Set<String>,
            limit: Int,
            offset: Int,
        ): ApiResult<SearchPage> {
            calls += Call(query, kinds, offset)
            return pages.removeFirstOrNull() ?: ApiResult.Ok(SearchPage(emptyList()))
        }
    }

    private val api = FakeSearch()
    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private fun newViewModel() = SearchViewModel(api, clock)

    private fun hit(
        kind: String,
        id: String,
        conversationId: String? = null,
        title: String? = null,
    ) = Hit(kind, id, 1.0, title, "a <mark>b</mark> c", "2026-09-28T10:00:00Z", conversationId)

    @Test
    fun `searches after 300 ms, once, with the last text`() =
        runTest(main.dispatcher) {
            val viewModel = newViewModel()

            viewModel.setQuery("k")
            advanceTimeBy(200)
            viewModel.setQuery("ky")
            advanceTimeBy(299)
            assertTrue(api.calls.isEmpty())
            advanceTimeBy(2)

            assertEquals(listOf("ky"), api.calls.map { it.query })
        }

    @Test
    fun `a query without a letter or digit does not search`() =
        runTest(main.dispatcher) {
            val viewModel = newViewModel()

            viewModel.setQuery(" -- ")
            advanceUntilIdle()

            assertTrue(api.calls.isEmpty())
        }

    @Test
    fun `hits map to rows and a memory without a source does not open`() =
        runTest(main.dispatcher) {
            api.pages +=
                ApiResult.Ok(
                    SearchPage(
                        listOf(
                            hit("conversation", "c1", title = "Lunch"),
                            hit("memory", "m1", conversationId = "c2"),
                            hit("memory", "m2"),
                        ),
                    ),
                )
            val viewModel = newViewModel()

            viewModel.setQuery("lunch")
            advanceUntilIdle()

            val rows = viewModel.state.value.rows
            assertEquals(listOf("c1", "c2", null), rows.map { it.openId })
            assertEquals("Lunch", rows[0].title)
            assertNull(rows[1].title)
            assertEquals("Yesterday", rows[0].date)
        }

    @Test
    fun `a filter searches again at once with its kinds`() =
        runTest(main.dispatcher) {
            val viewModel = newViewModel()
            viewModel.setQuery("kyiv")
            advanceUntilIdle()

            viewModel.setFilter(SearchFilter.Memories)
            advanceUntilIdle()

            assertEquals(setOf("memory"), api.calls.last().kinds)
            assertEquals(2, api.calls.size)
        }

    @Test
    fun `pages by nextOffset`() =
        runTest(main.dispatcher) {
            api.pages += ApiResult.Ok(SearchPage(listOf(hit("conversation", "c1")), nextOffset = 20))
            api.pages += ApiResult.Ok(SearchPage(listOf(hit("conversation", "c2"))))
            val viewModel = newViewModel()
            viewModel.setQuery("kyiv")
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(0, 20), api.calls.map { it.offset })
            assertEquals(2, viewModel.state.value.rows.size)
            assertTrue(viewModel.state.value.endReached)
        }

    @Test
    fun `an older server says it needs an update`() =
        runTest(main.dispatcher) {
            api.pages += ApiResult.Failure(FailureKind.NotFound, "Not found.")
            val viewModel = newViewModel()

            viewModel.setQuery("kyiv")
            advanceUntilIdle()

            assertEquals("This server needs an update", viewModel.state.value.error)
        }

    @Test
    fun `marks split into bold spans and the rest stays plain`() {
        assertEquals(
            listOf(SnippetSpan("a ", false), SnippetSpan("b", true), SnippetSpan(" c <b>", false)),
            SearchViewModel.markSpans("a <mark>b</mark> c <b>"),
        )
        assertEquals(listOf(SnippetSpan("x <mark>y", false)), SearchViewModel.markSpans("x <mark>y"))
    }
}
