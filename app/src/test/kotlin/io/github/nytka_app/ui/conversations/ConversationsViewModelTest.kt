package io.github.nytka_app.ui.conversations

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.conversations.FakeConversations.Companion.summary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun ConversationsViewModel.ids() =
        state.value.days
            .flatMap { it.rows }
            .map { it.id }

    @Test
    fun `refreshes when the screen is shown and every 30 seconds after, until it is left`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            val shown = backgroundScope.launch { viewModel.keepFresh() }

            runCurrent()
            assertEquals("the first load, then the refresh that comes with being shown", 2, api.requestedBefore.size)

            advanceTimeBy(ConversationsViewModel.REFRESH_MS - 1)
            runCurrent()
            assertEquals(2, api.requestedBefore.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(3, api.requestedBefore.size)
            advanceTimeBy(ConversationsViewModel.REFRESH_MS)
            runCurrent()
            assertEquals(4, api.requestedBefore.size)

            shown.cancel()
            advanceTimeBy(10 * ConversationsViewModel.REFRESH_MS)
            runCurrent()
            assertEquals("the app went to the background", 4, api.requestedBefore.size)
        }

    @Test
    fun `showing the screen again refreshes at once`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            val first = backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()
            first.cancel()
            val requests = api.requestedBefore.size

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(requests + 1, api.requestedBefore.size)
        }

    @Test
    fun `a refresh in the background leaves the pull-to-refresh spinner alone`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            val gate = CompletableDeferred<Unit>()
            api.gates += gate

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertFalse(viewModel.state.value.refreshing)
            assertFalse(viewModel.state.value.loading)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("b", "a"), viewModel.ids())
        }

    @Test
    fun `a new conversation comes in at the top and the pages already loaded stay`() =
        runTest {
            api.pages[null] =
                ApiResult.Ok(
                    ConversationPage(
                        listOf(summary("b", "2026-09-29T09:00:00Z", "2026-09-29T09:20:00Z")),
                        "2026-09-29T09:00:00Z",
                    ),
                )
            api.pages["2026-09-29T09:00:00Z"] =
                ApiResult.Ok(
                    ConversationPage(listOf(summary("a", "2026-09-28T18:00:00Z", "2026-09-28T18:05:00Z")), null),
                )
            val viewModel = ConversationsViewModel(api, clock)
            viewModel.loadMore()
            assertEquals(listOf("b", "a"), viewModel.ids())
            api.pages[null] =
                ApiResult.Ok(
                    ConversationPage(
                        listOf(summary("c", "2026-09-29T11:00:00Z", "2026-09-29T11:10:00Z")),
                        "2026-09-29T11:00:00Z",
                    ),
                )

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(listOf("c", "b", "a"), viewModel.ids())
            assertTrue(viewModel.state.value.endReached)
        }

    @Test
    fun `a refresh keeps the cursor of the deepest page loaded`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            api.pages["2026-09-28T18:00:00Z"] =
                ApiResult.Ok(
                    ConversationPage(
                        listOf(summary("z", "2026-09-20T08:00:00Z", "2026-09-20T08:01:00Z")),
                        "2026-09-20T08:00:00Z",
                    ),
                )
            api.pages["2026-09-20T08:00:00Z"] =
                ApiResult.Ok(
                    ConversationPage(listOf(summary("y", "2026-09-10T08:00:00Z", "2026-09-10T08:01:00Z")), null),
                )
            val viewModel = ConversationsViewModel(api, clock)
            viewModel.loadMore()

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()
            viewModel.loadMore()

            assertEquals(listOf("b", "a", "z", "y"), viewModel.ids())
            assertTrue(viewModel.state.value.endReached)
        }

    @Test
    fun `a conversation deleted elsewhere drops out of the list`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            api.pages[null] =
                ApiResult.Ok(
                    ConversationPage(listOf(summary("a", "2026-09-28T18:00:00Z", "2026-09-28T18:05:00Z")), null),
                )

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(listOf("a"), viewModel.ids())
        }

    @Test
    fun `a failed refresh in the background changes nothing`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            api.pages[null] = ApiResult.Failure(FailureKind.Network, "timeout")

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(listOf("b", "a"), viewModel.ids())
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a refresh in the background recovers a list that failed to load`() =
        runTest {
            api.pages[null] = ApiResult.Failure(FailureKind.Network, "timeout")
            val viewModel = ConversationsViewModel(api, clock)
            assertEquals("timeout", viewModel.state.value.error)
            api.pages[null] = ApiResult.Ok(firstPage)

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(listOf("b", "a"), viewModel.ids())
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `no second request goes out while a load is under way`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val gate = CompletableDeferred<Unit>()
            api.gates += gate
            val viewModel = ConversationsViewModel(api, clock)

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()

            assertEquals(listOf<String?>(null), api.requestedBefore)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("b", "a"), viewModel.ids())
        }

    @Test
    fun `an answer that lost the race to a newer load is dropped`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = ConversationsViewModel(api, clock)
            val gate = CompletableDeferred<Unit>()
            api.gates += gate
            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()
            // The refresh in the background is waiting with the old answer; a pull brings a newer one.
            api.pages[null] =
                ApiResult.Ok(
                    ConversationPage(listOf(summary("c", "2026-09-29T11:00:00Z", "2026-09-29T11:10:00Z")), null),
                )
            viewModel.refresh()
            assertEquals(listOf("c"), viewModel.ids())

            gate.complete(Unit)
            runCurrent()

            assertEquals(listOf("c"), viewModel.ids())
        }
}
