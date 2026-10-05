package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerStatus
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.ui.conversations.FakeConversations.Companion.summary
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.ListEmpty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The Media chip and the Hide media filter of the Conversations list (server feature `speech-kind`). */
class ConversationsMediaTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val speech = FakeSpeech()
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.18.0", 1, features = listOf("tags")))

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        ConversationsViewModel(
            api,
            StatusClient { ApiResult.Ok(ServerStatus(pendingChunks = 0)) },
            clock,
            FakeTags(),
            InfoClient { info },
            speech,
            savedState,
        )

    private fun ConversationsViewModel.ids() = state.value.days.flatMap { day -> day.rows.map { it.id } }

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

    private fun withSpeechKind() {
        info = ApiResult.Ok(ServerInfo("0.18.0", 1, features = listOf("tags", ServerInfo.FEATURE_SPEECH_KIND)))
    }

    private fun share(
        id: String,
        mediaShare: Double,
    ) = summary(id, "2026-09-29T09:00:00Z", "2026-09-29T09:20:00Z").copy(mediaShare = mediaShare)

    @Test
    fun `a row is media from a share of 0_8, not below`() {
        api.pages[null] = ApiResult.Ok(ConversationPage(listOf(share("a", 0.8), share("b", 0.79), share("c", 1.0))))

        val rows =
            newViewModel()
                .state.value.days
                .single()
                .rows

        assertEquals(listOf(true, false, true), rows.map { it.media })
    }

    @Test
    fun `a row without a share is not media`() {
        api.pages[null] = ApiResult.Ok(firstPage)

        assertTrue(
            newViewModel()
                .state.value.days
                .flatMap { it.rows }
                .none { it.media },
        )
    }

    @Test
    fun `the action shows only with speech-kind`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        assertFalse(newViewModel().mediaFilter.value)

        withSpeechKind()
        assertTrue(newViewModel().mediaFilter.value)
    }

    @Test
    fun `without speech-kind hiding media makes no call and keeps the list`() {
        api.pages[null] = ApiResult.Ok(firstPage)
        val viewModel = newViewModel()

        viewModel.hideMedia()

        assertTrue(speech.hiddenRequests.isEmpty())
        assertFalse(viewModel.state.value.hideMedia)
        assertEquals(listOf<String?>(null), api.requestedBefore)
    }

    @Test
    fun `hiding media reloads with media=hide and pages the same way`() {
        withSpeechKind()
        api.pages[null] = ApiResult.Ok(firstPage)
        speech.hiddenPages[null] = ApiResult.Ok(workPage)
        speech.hiddenPages["2026-09-29T10:00:00Z"] =
            ApiResult.Ok(
                ConversationPage(listOf(summary("w1", "2026-09-28T10:00:00Z", "2026-09-28T10:10:00Z"))),
            )
        val viewModel = newViewModel()

        viewModel.hideMedia()
        viewModel.loadMore()

        assertEquals(
            listOf<Pair<String?, String?>>(null to null, null to "2026-09-29T10:00:00Z"),
            speech.hiddenRequests,
        )
        assertEquals(listOf("w2", "w1"), viewModel.ids())
        assertTrue(viewModel.state.value.hideMedia)
        assertTrue(viewModel.state.value.endReached)
    }

    @Test
    fun `showing media again returns to the full list`() {
        withSpeechKind()
        api.pages[null] = ApiResult.Ok(firstPage)
        speech.hiddenPages[null] = ApiResult.Ok(workPage)
        val viewModel = newViewModel()
        viewModel.hideMedia()

        viewModel.showMedia()

        assertEquals(listOf("b", "a"), viewModel.ids())
        assertFalse(viewModel.state.value.hideMedia)
    }

    @Test
    fun `the media filter and a tag filter combine`() {
        withSpeechKind()
        api.pages[null] = ApiResult.Ok(firstPage)
        speech.hiddenPages[null] = ApiResult.Ok(workPage)
        val viewModel = newViewModel()
        viewModel.hideMedia()

        viewModel.showTag("work")

        assertEquals("work" to null, speech.hiddenRequests.last())
    }

    @Test
    fun `an empty list under the filter is told apart`() {
        withSpeechKind()
        api.pages[null] = ApiResult.Ok(firstPage)
        speech.hiddenPages[null] = ApiResult.Ok(ConversationPage(emptyList()))
        val viewModel = newViewModel()

        viewModel.hideMedia()

        assertEquals(ListEmpty.Empty, viewModel.state.value.empty)
        assertTrue(viewModel.state.value.hideMedia)
    }

    @Test
    fun `the filter survives recreation through the saved state`() {
        withSpeechKind()
        api.pages[null] = ApiResult.Ok(firstPage)
        speech.hiddenPages[null] = ApiResult.Ok(workPage)
        val saved = SavedStateHandle()
        newViewModel(saved).hideMedia()
        api.requestedBefore.clear()

        val again = newViewModel(saved)

        assertTrue(again.state.value.hideMedia)
        assertEquals(listOf("w2"), again.ids())
        assertTrue(api.requestedBefore.isEmpty())
    }

    @Test
    fun `a saved filter on a server that lost speech-kind falls back to the full list`() {
        api.pages[null] = ApiResult.Ok(firstPage)

        val viewModel = newViewModel(SavedStateHandle(mapOf("hideMedia" to true)))

        assertTrue(speech.hiddenRequests.isEmpty())
        assertFalse(viewModel.state.value.hideMedia)
        assertEquals(listOf("b", "a"), viewModel.ids())
    }
}
