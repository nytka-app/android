package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Bookmark
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.ui.tasks.FakeTasks
import io.github.nytka_app.ui.tasks.FakeTasks.Companion.task
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

    private val tasks = FakeTasks(listOf(task("t1"), task("t2", done = true)))
    private val bookmarks = FakeBookmarks()

    private fun viewModel() = ConversationViewModel(SavedStateHandle(mapOf("id" to "c1")), api, tasks, bookmarks, clock)

    private fun detail(
        status: String = "closed",
        aiStatus: String = "done",
        title: String? = "Planning the launch",
        segments: List<Segment> = emptyList(),
    ) = (api.detail as ApiResult.Ok).value.copy(
        status = status,
        aiStatus = aiStatus,
        title = title,
        summary = "They agreed on Friday.",
        tasks = listOf(task("t1", "Send the deck"), task("t2", "Book a room", done = true)),
        segments = segments,
    )

    private fun segment(
        id: Long,
        speaker: String?,
    ) = Segment(id, "2026-09-29T08:0$id:00Z", "2026-09-29T08:0$id:04Z", "Line $id.", speaker)

    @Test
    fun `shows the segments as timestamped paragraphs`() {
        val state = viewModel().state.value

        assertEquals("Today", state.title)
        assertEquals("08:00–08:10", state.timeRange)
        assertEquals("10 min", state.length)
        assertEquals(listOf(Paragraph("08:00", "Hello."), Paragraph("08:03", "Bye.")), state.paragraphs)
    }

    @Test
    fun `a v0_1 conversation shows the day and no summary, tasks or chip`() {
        val state = viewModel().state.value

        assertEquals("Today", state.title)
        assertNull(state.summary)
        assertNull(state.chip)
        assertTrue(state.tasks.isEmpty())
    }

    @Test
    fun `title summary and tasks show once the model has run`() {
        api.detail = ApiResult.Ok(detail())

        val state = viewModel().state.value

        assertEquals("Planning the launch", state.title)
        assertEquals("They agreed on Friday.", state.summary)
        assertEquals(listOf(TaskLine("t1", "Send the deck", false), TaskLine("t2", "Book a room", true)), state.tasks)
    }

    @Test
    fun `the chip follows the ai status`() {
        api.detail = ApiResult.Ok(detail(aiStatus = "pending"))
        assertEquals("Summarizing", viewModel().state.value.chip)

        api.detail = ApiResult.Ok(detail(aiStatus = "failed"))
        assertEquals("Summary failed", viewModel().state.value.chip)
    }

    @Test
    fun `speakers show above each run, colored by first appearance`() {
        api.detail =
            ApiResult.Ok(
                detail(
                    segments =
                        listOf(
                            segment(1, "Anna"),
                            segment(2, "Anna"),
                            segment(3, "SPEAKER_01"),
                            segment(4, "Anna"),
                            segment(5, null),
                        ),
                ),
            )

        val paragraphs = viewModel().state.value.paragraphs

        assertEquals(listOf(true, false, true, true, false), paragraphs.map { it.showSpeaker })
        assertEquals(listOf(0, 0, 1, 0, 0), paragraphs.map { it.speakerColor })
        assertNull(paragraphs.last().speaker)
    }

    @Test
    fun `the wearer shows as Me, a named voice by its name, and only other voices can be named`() {
        api.detail =
            ApiResult.Ok(
                detail(
                    segments =
                        listOf(
                            segment(1, "SPEAKER_0").copy(speakerId = "0", isUser = true),
                            segment(2, "SPEAKER_4").copy(speakerId = "4", isUser = false, personName = "Anna"),
                            segment(3, "SPEAKER_5").copy(speakerId = "5", isUser = false),
                            segment(4, "SPEAKER_5"),
                        ),
                ),
            )

        val paragraphs = viewModel().state.value.paragraphs

        assertEquals(listOf("Me", "Anna", "SPEAKER_5", "SPEAKER_5"), paragraphs.map { it.speaker })
        assertEquals(listOf(null, "4", "5", null), paragraphs.map { it.voiceId })
    }

    @Test
    fun `naming a voice sends it and reads the transcript again`() {
        api.detail =
            ApiResult.Ok(detail(segments = listOf(segment(1, "SPEAKER_4").copy(speakerId = "4", isUser = false))))
        val viewModel = viewModel()
        api.detail =
            ApiResult.Ok(
                detail(
                    segments =
                        listOf(
                            segment(1, "SPEAKER_4").copy(speakerId = "4", isUser = false, personName = "Anna"),
                        ),
                ),
            )

        viewModel.nameVoice("4", "  Anna ")

        assertEquals(listOf("4" to "Anna"), api.named)
        assertEquals(
            "Anna",
            viewModel.state.value.paragraphs
                .single()
                .speaker,
        )
    }

    @Test
    fun `an empty name is not sent`() {
        val viewModel = viewModel()

        viewModel.nameVoice("4", "   ")

        assertEquals(emptyList<Pair<String, String>>(), api.named)
    }

    @Test
    fun `a seventh speaker starts the colors over`() {
        api.detail = ApiResult.Ok(detail(segments = (1L..7L).map { segment(it, "S$it") }))

        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 0),
            viewModel()
                .state.value.paragraphs
                .map { it.speakerColor },
        )
    }

    @Test
    fun `renaming sends the title and shows it`() {
        val viewModel = viewModel()

        viewModel.rename("  My title ")

        assertEquals(listOf("c1" to "My title"), api.renamed)
        assertEquals("My title", viewModel.state.value.title)
    }

    @Test
    fun `an empty title restores the generated one`() {
        val viewModel = viewModel()

        viewModel.rename("   ")

        assertEquals(listOf("c1" to null), api.renamed)
        assertEquals("Today", viewModel.state.value.title)
    }

    @Test
    fun `renaming on a v0_1 server says it needs an update`() {
        api.renameAnswer = ApiResult.Failure(FailureKind.Unsupported, "x")
        val viewModel = viewModel()

        viewModel.rename("x")

        assertEquals("This server needs an update", viewModel.state.value.error)
    }

    @Test
    fun `renaming a conversation that is gone says so`() {
        api.renameAnswer = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        val viewModel = viewModel()

        viewModel.rename("x")

        assertEquals("This item no longer exists", viewModel.state.value.error)
        assertEquals("Today", viewModel.state.value.title)
    }

    @Test
    fun `regenerate queues a run and shows Summarizing`() {
        api.detail = ApiResult.Ok(detail())
        val viewModel = viewModel()

        viewModel.regenerate()

        assertEquals(listOf("c1"), api.enriched)
        assertEquals("Summarizing", viewModel.state.value.chip)
    }

    @Test
    fun `regenerate is off while the conversation is open`() {
        api.detail = ApiResult.Ok(detail(status = "open"))
        val viewModel = viewModel()

        viewModel.regenerate()

        assertTrue(viewModel.state.value.open)
        assertTrue(api.enriched.isEmpty())
    }

    @Test
    fun `regenerate without a model explains the refusal`() {
        api.enrichAnswer = ApiResult.Failure(FailureKind.Conflict, "This conflicts with what the server holds.")
        val viewModel = viewModel()

        viewModel.regenerate()

        assertTrue(
            viewModel.state.value.error!!
                .contains("language model"),
        )
    }

    @Test
    fun `a summary in the making is looked for until it arrives`() =
        runTest {
            api.detail = ApiResult.Ok(detail(aiStatus = "pending", title = null))
            val viewModel = viewModel()
            api.detail = ApiResult.Ok(detail())

            viewModel.keepFresh()

            assertNull(viewModel.state.value.chip)
            assertEquals("Planning the launch", viewModel.state.value.title)
        }

    @Test
    fun `ticking a task saves it and stays ticked`() {
        api.detail = ApiResult.Ok(detail())
        val viewModel = viewModel()

        viewModel.setTaskDone("t1", true)

        assertTrue(
            viewModel.state.value.tasks
                .first { it.id == "t1" }
                .done,
        )
        assertTrue(tasks.all.first { it.id == "t1" }.done)
    }

    @Test
    fun `a refused tick is put back`() {
        api.detail = ApiResult.Ok(detail())
        tasks.failure = ApiResult.Failure(FailureKind.Forbidden, "no")
        val viewModel = viewModel()

        viewModel.setTaskDone("t1", true)

        assertFalse(
            viewModel.state.value.tasks
                .first { it.id == "t1" }
                .done,
        )
        assertEquals("The task could not be saved.", viewModel.state.value.error)
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

        assertEquals("This item no longer exists", viewModel().state.value.error)
    }

    private fun withBookmarks(
        vararg at: String,
        segments: List<Segment> = api.detail.let { (it as ApiResult.Ok).value.segments },
    ): ConversationUiState {
        api.detail =
            ApiResult.Ok(detail(segments = segments).copy(bookmarks = at.mapIndexed { i, t -> Bookmark("b$i", t) }))
        return viewModel().state.value
    }

    @Test
    fun `a bookmark inside a segment marks that paragraph`() {
        val state = withBookmarks("2026-09-29T08:03:02Z")

        assertEquals(
            listOf(emptyList<BookmarkMark>(), listOf(BookmarkMark("b0"))),
            state.paragraphs.map { it.bookmarks },
        )
    }

    @Test
    fun `a bookmark between two segments marks the nearer one, the earlier on a tie`() {
        // Segments end 08:00:09 and start 08:03:00: 08:00:30 is nearer the first, 08:02:00 the second.
        val nearFirst = withBookmarks("2026-09-29T08:00:30Z", "2026-09-29T08:01:34.500Z", "2026-09-29T08:02:00Z")

        assertEquals(
            listOf(listOf("b0", "b1"), listOf("b2")),
            nearFirst.paragraphs.map { p -> p.bookmarks.map { it.id } },
        )
    }

    @Test
    fun `a bookmark before the first or after the last segment marks the edge paragraph`() {
        val state = withBookmarks("2026-09-29T07:59:00Z", "2026-09-29T08:09:00Z")

        assertEquals(listOf(listOf("b0"), listOf("b1")), state.paragraphs.map { p -> p.bookmarks.map { it.id } })
    }

    @Test
    fun `a conversation without segments lists its bookmarks on their own`() {
        val state = withBookmarks("2026-09-29T08:03:02Z", segments = emptyList())

        assertEquals(listOf(BookmarkMark("b0")), state.looseBookmarks)
    }

    @Test
    fun `a server before v0_8 shows no bookmarks`() {
        val state = viewModel().state.value

        assertTrue(state.paragraphs.all { it.bookmarks.isEmpty() })
        assertTrue(state.looseBookmarks.isEmpty())
    }

    @Test
    fun `a saved note shows on its bookmark, an empty one clears it`() {
        withBookmarks("2026-09-29T08:03:02Z")
        val model = viewModel()

        model.setBookmarkNote("b0", "  Call Anna  ")
        assertEquals(listOf("b0" to "Call Anna"), bookmarks.notes)
        assertEquals(
            "Call Anna",
            model.state.value.paragraphs[1]
                .bookmarks
                .single()
                .note,
        )

        model.setBookmarkNote("b0", "")
        assertNull(
            model.state.value.paragraphs[1]
                .bookmarks
                .single()
                .note,
        )
    }

    @Test
    fun `a note the server refuses leaves the bookmark as it was and says so`() {
        withBookmarks("2026-09-29T08:03:02Z")
        bookmarks.answer = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        val model = viewModel()

        model.setBookmarkNote("b0", "x")

        assertNull(
            model.state.value.paragraphs[1]
                .bookmarks
                .single()
                .note,
        )
        assertEquals("This item no longer exists", model.state.value.error)
    }
}
