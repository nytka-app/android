package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioIndex
import io.github.nytka_app.core.api.AudioRun
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A link to a moment, `conversation/{id}?at=`: Today and the Timeline open a conversation where an item was said. */
class ConversationMomentTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val starts = listOf(Instant.parse("2026-09-29T08:00:05Z"), Instant.parse("2026-09-29T08:03:00Z"))
    private val index =
        AudioIndex(
            durationMs = 20_000,
            runs =
                listOf(
                    AudioRun(0, "2026-09-29T08:00:00Z", "2026-09-29T08:00:10Z"),
                    AudioRun(10_000, "2026-09-29T08:03:00Z", "2026-09-29T08:03:10Z"),
                ),
        )
    private val audio = FakeAudio()
    private val player = FakePlayer()

    private fun ms(text: String) = Instant.parse(text).toEpochMilli().toString()

    private fun viewModel(at: String?) =
        ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1", "at" to at)),
            FakeConversations().apply {
                detail =
                    ApiResult.Ok(
                        ConversationDetail(
                            id = "c1",
                            startedAt = "2026-09-29T08:00:00Z",
                            endedAt = "2026-09-29T08:10:00Z",
                            status = "closed",
                            segments =
                                listOf(
                                    Segment(1, "2026-09-29T08:00:05Z", "2026-09-29T08:00:09Z", "Hello."),
                                    Segment(2, "2026-09-29T08:03:00Z", "2026-09-29T08:03:04Z", "Bye."),
                                ),
                        ),
                    )
            },
            FakeTasks(emptyList()),
            FakeBookmarks(),
            FakeReview(),
            audio,
            player,
            Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC),
            fakeInfo(),
            FakeTags(),
            FakeSpeech(),
        )

    @Test
    fun `the screen focuses the paragraph said then and moves the playhead there without playing`() {
        audio.index = ApiResult.Ok(index)

        val model = viewModel(ms("2026-09-29T08:03:02Z"))

        assertEquals(1, model.state.value.focus)
        assertEquals(listOf("seek 10000"), player.calls)
        model.focusShown()
        assertNull(model.state.value.focus)
    }

    @Test
    fun `without a moment nothing is focused and nothing moves`() {
        audio.index = ApiResult.Ok(index)

        assertNull(viewModel(null).state.value.focus)
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun `a moment before the first line is the first paragraph`() {
        assertEquals(0, MomentLink(ms("2026-09-29T07:00:00Z")).focus(starts))
    }

    @Test
    fun `focus and seek each happen once, and only when what they need is in`() {
        val link = MomentLink(ms("2026-09-29T08:03:02Z"))

        assertNull(link.focus(emptyList()))
        assertEquals(1, link.focus(starts))
        assertNull(link.focus(starts))
        assertNull(link.position(starts, null))
        assertEquals(10_000L, link.position(starts, index))
        assertNull(link.position(starts, index))
    }

    @Test
    fun `an unreadable moment opens at the start`() {
        assertNull(MomentLink("soon").focus(starts))
    }
}
