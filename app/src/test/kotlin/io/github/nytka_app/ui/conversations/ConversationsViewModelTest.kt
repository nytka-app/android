package io.github.nytka_app.ui.conversations

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.conversations.FakeConversations.Companion.summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ConversationsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()

    private val firstPage =
        ConversationPage(
            items =
                listOf(
                    summary("b", "2026-09-29T09:00:00Z", "2026-09-29T09:20:00Z", "hello there"),
                    summary("a", "2026-09-28T18:00:00Z", "2026-09-28T18:05:00Z"),
                ),
            nextBefore = "2026-09-28T18:00:00Z",
        )

    @Test
    fun `groups the first page by day`() {
        api.pages[null] = ApiResult.Ok(firstPage)

        val state = ConversationsViewModel(api, clock).state.value

        assertEquals(listOf("Today", "Yesterday"), state.days.map { it.title })
        assertEquals(ConversationRow("b", "09:00–09:20", "20 min", "hello there"), state.days[0].rows.single())
    }

    @Test
    fun `loads more with nextBefore until the last page`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        api.pages["2026-09-28T18:00:00Z"] =
            ApiResult.Ok(
                ConversationPage(
                    listOf(summary("z", "2026-09-20T08:00:00Z", "2026-09-20T08:01:00Z")),
                    nextBefore = null,
                ),
            )
        val viewModel = ConversationsViewModel(api, clock)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(listOf(null, "2026-09-28T18:00:00Z"), api.requestedBefore)
        assertEquals(
            3,
            viewModel.state.value.days
                .sumOf { it.rows.size },
        )
        assertTrue(viewModel.state.value.endReached)
    }

    @Test
    fun `refresh starts over`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        val viewModel = ConversationsViewModel(api, clock)

        viewModel.refresh()

        assertEquals(listOf<String?>(null, null), api.requestedBefore)
        assertEquals(
            2,
            viewModel.state.value.days
                .sumOf { it.rows.size },
        )
    }

    @Test
    fun `a deleted conversation leaves the list`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        val viewModel = ConversationsViewModel(api, clock)

        viewModel.forget("b")

        assertEquals(
            listOf("Yesterday"),
            viewModel.state.value.days
                .map { it.title },
        )
        assertEquals(listOf<String?>(null), api.requestedBefore)
    }

    @Test
    fun `an error keeps what was loaded`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        api.pages["2026-09-28T18:00:00Z"] = ApiResult.Failure(FailureKind.Network, "timeout")
        val viewModel = ConversationsViewModel(api, clock)

        viewModel.loadMore()

        assertEquals(
            2,
            viewModel.state.value.days
                .sumOf { it.rows.size },
        )
        assertEquals("timeout", viewModel.state.value.error)
    }
}
