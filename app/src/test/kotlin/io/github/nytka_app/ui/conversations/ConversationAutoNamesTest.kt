package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.conversations.FakeReview.Companion.suggestion
import io.github.nytka_app.ui.people.UndoNotice
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The markers on speakers the server named by itself, and their Undo. */
class ConversationAutoNamesTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val review = FakeReview()
    private var info = fakeInfo("auto-names")
    private var reads = 0

    private val here = suggestion("a1", conversationId = "c1")
    private val elsewhere = suggestion("a2", conversationId = "c2", name = "Petro")

    init {
        api.detail =
            ApiResult.Ok(
                ConversationDetail("c1", "2026-10-20T08:00:00Z", "2026-10-20T08:10:00Z", "closed", emptyList()),
            )
        review.autoApplied = ApiResult.Ok(listOf(here, elsewhere))
    }

    private fun viewModel() =
        ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1")),
            api,
            FakeTasks(emptyList()),
            FakeBookmarks(),
            review,
            FakeAudio(),
            FakePlayer(),
            clock,
            info,
            FakeTags(),
            FakeSpeech(),
        )

    @Test
    fun `only this conversation's auto-applied names are marked, and only where the server lists auto-names`() {
        assertEquals(listOf(here), viewModel().state.value.autoNamed)

        info = fakeInfo()
        assertTrue(
            viewModel()
                .state.value.autoNamed
                .isEmpty(),
        )
    }

    @Test
    fun `undo takes the name back, reads the transcript again and confirms it`() {
        val viewModel = viewModel()
        api.detail =
            ApiResult.Ok(
                ConversationDetail(
                    "c1",
                    "2026-10-20T08:00:00Z",
                    "2026-10-20T08:10:00Z",
                    "closed",
                    emptyList(),
                    title = "After",
                ),
            )

        viewModel.undoAutoName(here)

        assertEquals(listOf("a1"), review.undone)
        assertTrue(
            viewModel.state.value.autoNamed
                .isEmpty(),
        )
        assertEquals("After", viewModel.state.value.title)
        assertEquals(UndoNotice.Undone("Olena"), viewModel.state.value.undoNotice)
    }

    @Test
    fun `a refused undo puts the marker back, a 409 does not`() {
        review.undoAnswer = ApiResult.Failure(FailureKind.Network, "No connection.")
        val viewModel = viewModel()
        viewModel.undoAutoName(here)
        assertEquals(listOf(here), viewModel.state.value.autoNamed)

        review.undoAnswer = ApiResult.Failure(FailureKind.Conflict, "")
        viewModel.undoAutoName(here)
        assertTrue(
            viewModel.state.value.autoNamed
                .isEmpty(),
        )
        assertEquals(UndoNotice.AlreadyUndone, viewModel.state.value.undoNotice)
    }
}
