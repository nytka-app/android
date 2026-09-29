package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ConversationViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api =
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
        }

    private fun viewModel() = ConversationViewModel(SavedStateHandle(mapOf("id" to "c1")), api, clock)

    @Test
    fun `shows the segments as timestamped paragraphs`() {
        val state = viewModel().state.value

        assertEquals("Today", state.title)
        assertEquals("08:00–08:10", state.timeRange)
        assertEquals("10 min", state.length)
        assertEquals(listOf(Paragraph("08:00", "Hello."), Paragraph("08:03", "Bye.")), state.paragraphs)
    }

    @Test
    fun `delete asks the server and reports done`() {
        val viewModel = viewModel()

        viewModel.delete()

        assertEquals(listOf("c1"), api.deleted)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `raw transcription loads on request`() {
        api.raw = ApiResult.Ok("""[{"id":1}]""")
        val viewModel = viewModel()

        viewModel.loadRaw()

        assertEquals("""[{"id":1}]""", viewModel.state.value.raw)
    }

    @Test
    fun `a missing conversation says so`() {
        api.detail = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        assertEquals("This conversation no longer exists.", viewModel().state.value.error)
    }
}
