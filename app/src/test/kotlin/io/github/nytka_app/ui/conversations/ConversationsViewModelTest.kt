package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerStatus
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.core.api.Tag
import io.github.nytka_app.core.api.TagSuggestion
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.conversations.FakeConversations.Companion.summary
import io.github.nytka_app.ui.tags.ListEmpty
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
    private var serverStatus: ApiResult<ServerStatus> = ApiResult.Ok(ServerStatus(pendingChunks = 0))
    private var statusCalls = 0

    /** Only the filtered list is read from here; every other call is a test bug. */
    private class FakeTagLists : TagsClient {
        val pages = mutableMapOf<String?, ApiResult<ConversationPage>>()
        val requested = mutableListOf<Pair<String, String?>>()

        override suspend fun conversations(
            tag: String,
            before: String?,
            limit: Int,
        ): ApiResult<ConversationPage> {
            requested += tag to before
            return pages.getValue(before)
        }

        override suspend fun tags(q: String?): ApiResult<List<Tag>> = error("unused")

        override suspend fun addConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun removeConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun addPersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun removePersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun people(tag: String): ApiResult<List<io.github.nytka_app.core.api.Person>> = error("unused")

        override suspend fun suggestions(): ApiResult<List<TagSuggestion>> = error("unused")

        override suspend fun answerSuggestion(
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = error("unused")
    }

    private val tagLists = FakeTagLists()
    private val speech = FakeSpeech()
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.17.0", 1, features = listOf("tags")))

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        ConversationsViewModel(
            api,
            StatusClient {
                statusCalls++
                serverStatus
            },
            clock,
            tagLists,
            InfoClient { info },
            speech,
            savedState,
        )

    private val workPage =
        ConversationPage(
            items = listOf(summary("w2", "2026-09-29T10:00:00Z", "2026-09-29T10:10:00Z")),
            nextBefore = "2026-09-29T10:00:00Z",
        )

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

        val state = newViewModel().state.value

        assertEquals(listOf("Today", "Yesterday"), state.days.map { it.title })
        assertEquals(ConversationRow("b", "09:00–09:20", "20 min", "hello there"), state.days[0].rows.single())
    }

    @Test
    fun `a row shows the title, the summary and a chip while summarizing`() {
        api.pages[null] =
            ApiResult.Ok(
                ConversationPage(
                    listOf(
                        summary("b", "2026-09-29T09:00:00Z", "2026-09-29T09:20:00Z").copy(
                            title = "Planning the launch",
                            summary = "They agreed on Friday.",
                            aiStatus = "done",
                        ),
                        summary("a", "2026-09-29T08:00:00Z", "2026-09-29T08:05:00Z").copy(aiStatus = "pending"),
                        summary("f", "2026-09-29T07:00:00Z", "2026-09-29T07:05:00Z").copy(aiStatus = "failed"),
                    ),
                ),
            )

        val rows =
            newViewModel()
                .state.value.days
                .single()
                .rows

        assertEquals(
            ConversationRow("b", "09:00–09:20", "20 min", "They agreed on Friday.", "Planning the launch", null),
            rows[0],
        )
        assertEquals("text a", rows[1].preview)
        assertNull(rows[1].title)
        assertEquals(SummaryChip.Summarizing, rows[1].chip)
        assertEquals(SummaryChip.Failed, rows[2].chip)
    }

    @Test
    fun `a time with an offset instead of Z is read`() {
        api.pages[null] =
            ApiResult.Ok(
                ConversationPage(listOf(summary("b", "2026-09-29T09:00:00+00:00", "2026-09-29T09:20:00+00:00"))),
            )

        assertEquals(
            "09:00–09:20",
            newViewModel()
                .state.value.days
                .single()
                .rows
                .single()
                .timeRange,
        )
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
        val viewModel = newViewModel()

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
        val viewModel = newViewModel()

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
        val viewModel = newViewModel()

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
        val viewModel = newViewModel()

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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()
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
            val viewModel = newViewModel()

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
            val viewModel = newViewModel()
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

    @Test
    fun `audio the server has held for over a quarter of an hour shows on the status card`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        serverStatus = ApiResult.Ok(ServerStatus(12, "2026-09-29T11:30:00Z"))

        val viewModel = newViewModel()

        assertEquals("The server is behind: 12 chunks have waited since 11:30.", viewModel.notice.value)
    }

    @Test
    fun `a failed batch alone shows nothing, since the server never clears its error`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        serverStatus = ApiResult.Ok(ServerStatus(0, null, "The transcription endpoint answered 503."))

        assertNull(newViewModel().notice.value)
    }

    @Test
    fun `a server with nothing wrong shows nothing`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        serverStatus = ApiResult.Ok(ServerStatus(2, "2026-09-29T11:58:00Z"))

        assertNull(newViewModel().notice.value)
    }

    @Test
    fun `a status call that fails keeps what the card showed`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        serverStatus = ApiResult.Ok(ServerStatus(12, "2026-09-29T11:30:00Z"))
        val viewModel = newViewModel()
        serverStatus = ApiResult.Failure(FailureKind.Network, "timeout")

        viewModel.refresh()

        assertEquals("The server is behind: 12 chunks have waited since 11:30.", viewModel.notice.value)
    }

    @Test
    fun `pulling down asks the server again`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        val viewModel = newViewModel()
        assertNull(viewModel.notice.value)
        serverStatus = ApiResult.Ok(ServerStatus(12, "2026-09-29T11:30:00Z"))

        viewModel.refresh()

        assertEquals("The server is behind: 12 chunks have waited since 11:30.", viewModel.notice.value)
    }

    @Test
    fun `the notice follows the server every 30 seconds while the screen is shown`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            val viewModel = newViewModel()
            val shown = backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()
            assertNull(viewModel.notice.value)

            serverStatus = ApiResult.Ok(ServerStatus(12, "2026-09-29T11:30:00Z"))
            advanceTimeBy(ConversationsViewModel.REFRESH_MS)
            runCurrent()
            assertEquals("The server is behind: 12 chunks have waited since 11:30.", viewModel.notice.value)

            // The server catches up.
            serverStatus = ApiResult.Ok(ServerStatus(0))
            advanceTimeBy(ConversationsViewModel.REFRESH_MS)
            runCurrent()
            assertNull(viewModel.notice.value)

            shown.cancel()
            val calls = statusCalls
            advanceTimeBy(10 * ConversationsViewModel.REFRESH_MS)
            runCurrent()
            assertEquals(calls, statusCalls)
        }

    @Test
    fun `a tag from a chip shows the list filtered by it`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(workPage)
        val viewModel = newViewModel()

        viewModel.showTag("work")

        assertEquals("work", viewModel.state.value.tag)
        assertEquals(listOf("w2"), viewModel.ids())
        assertEquals(listOf("work" to null), tagLists.requested)
        assertEquals("only the first load read the full list", listOf<String?>(null), api.requestedBefore)
    }

    @Test
    fun `a filtered list pages with before`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(workPage)
        tagLists.pages["2026-09-29T10:00:00Z"] =
            ApiResult.Ok(ConversationPage(listOf(summary("w1", "2026-09-29T08:00:00Z", "2026-09-29T08:10:00Z"))))
        val viewModel = newViewModel()
        viewModel.showTag("work")

        viewModel.loadMore()

        assertEquals(listOf("w2", "w1"), viewModel.ids())
        assertEquals(listOf("work" to null, "work" to "2026-09-29T10:00:00Z"), tagLists.requested)
        assertTrue(viewModel.state.value.endReached)
    }

    @Test
    fun `clearing the tag reads the full list again`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(workPage)
        val viewModel = newViewModel()
        viewModel.showTag("work")

        viewModel.clearTag()

        assertNull(viewModel.state.value.tag)
        assertEquals(listOf("b", "a"), viewModel.ids())
        assertEquals(listOf<String?>(null, null), api.requestedBefore)
    }

    @Test
    fun `a server without the tags feature keeps the full list and makes no tag call`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        info = ApiResult.Ok(ServerInfo("0.16.0", 1, features = listOf("people")))
        val viewModel = newViewModel()

        viewModel.showTag("work")

        assertNull(viewModel.state.value.tag)
        assertEquals(listOf("b", "a"), viewModel.ids())
        assertTrue(tagLists.requested.isEmpty())
    }

    @Test
    fun `when the feature list cannot be read the full list stays`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        info = ApiResult.Failure(FailureKind.Network, "The server did not answer.")
        val viewModel = newViewModel()

        viewModel.showTag("work")

        assertNull(viewModel.state.value.tag)
        assertTrue(tagLists.requested.isEmpty())
    }

    @Test
    fun `the same tag twice reads the list once`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(workPage)
        val viewModel = newViewModel()

        viewModel.showTag("work")
        viewModel.showTag("work")

        assertEquals(1, tagLists.requested.size)
    }

    @Test
    fun `the refresh that keeps the list fresh keeps the filter`() =
        runTest {
            api.pages[null] = ApiResult.Ok(firstPage)
            tagLists.pages[null] = ApiResult.Ok(workPage)
            val viewModel = newViewModel()
            viewModel.showTag("work")
            val before = tagLists.requested.size

            backgroundScope.launch { viewModel.keepFresh() }
            runCurrent()
            advanceTimeBy(ConversationsViewModel.REFRESH_MS)
            runCurrent()

            assertEquals(before + 2, tagLists.requested.size)
            assertEquals(listOf("w2"), viewModel.ids())
            assertEquals(listOf<String?>(null), api.requestedBefore)
        }

    @Test
    fun `the tag survives a restore of the saved state`() {
        tagLists.pages[null] = ApiResult.Ok(workPage)

        val viewModel = newViewModel(SavedStateHandle(mapOf("tag" to "work")))

        assertEquals("work", viewModel.state.value.tag)
        assertEquals(listOf("w2"), viewModel.ids())
        assertTrue(api.requestedBefore.isEmpty())
    }

    @Test
    fun `a filtered list that cannot be read shows the error and keeps the tag`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Failure(FailureKind.NotFound, "The server answered.")
        val viewModel = newViewModel()

        viewModel.showTag("work")

        assertEquals("work", viewModel.state.value.tag)
        assertEquals("The server answered.", viewModel.state.value.error)
        assertTrue(viewModel.ids().isEmpty())
    }

    @Test
    fun `the tag action shows only on a server with the tags feature`() {
        api.pages[null] = ApiResult.Ok(firstPage)

        assertTrue(newViewModel().tagFilter.value)

        info = ApiResult.Ok(ServerInfo("0.16.0", 1, features = listOf("people")))
        assertFalse(newViewModel().tagFilter.value)

        info = ApiResult.Failure(FailureKind.Network, "The server did not answer.")
        assertFalse(newViewModel().tagFilter.value)
        assertTrue(tagLists.requested.isEmpty())
    }

    @Test
    fun `a filtered list with nothing in it is EmptyForTag`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(ConversationPage(emptyList()))
        val viewModel = newViewModel()

        viewModel.showTag("work")

        assertEquals(ListEmpty.EmptyForTag, viewModel.state.value.empty)
        assertEquals("work", viewModel.state.value.tag)
    }

    @Test
    fun `an empty full list is Empty, and a list with rows or an error is not`() {
        api.pages[null] = ApiResult.Ok(ConversationPage(emptyList()))
        assertEquals(ListEmpty.Empty, newViewModel().state.value.empty)

        api.pages[null] = ApiResult.Ok(firstPage)
        assertEquals(ListEmpty.NotEmpty, newViewModel().state.value.empty)

        api.pages[null] = ApiResult.Failure(FailureKind.Network, "The server did not answer.")
        assertEquals(ListEmpty.NotEmpty, newViewModel().state.value.empty)
    }

    @Test
    fun `clearing an empty filter leaves EmptyForTag`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        tagLists.pages[null] = ApiResult.Ok(ConversationPage(emptyList()))
        val viewModel = newViewModel()
        viewModel.showTag("work")

        viewModel.clearTag()

        assertEquals(ListEmpty.NotEmpty, viewModel.state.value.empty)
        assertNull(viewModel.state.value.tag)
    }
}
