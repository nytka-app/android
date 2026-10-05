package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Speech-kind chips and marks on the conversation screen. */
class ConversationSpeechTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val speech = FakeSpeech()

    private fun line(
        id: Long,
        kind: String? = null,
        guess: String? = null,
        marked: Boolean = false,
    ) = Segment(
        id,
        "2026-09-29T08:0$id:00Z",
        "2026-09-29T08:0$id:04Z",
        "Line $id.",
        speechKind = kind,
        speechGuess = guess,
        speechMarked = marked,
    )

    private fun viewModel(
        vararg segments: Segment,
        info: InfoClient = fakeInfo(ServerInfo.FEATURE_SPEECH_KIND),
    ): ConversationViewModel {
        api.detail =
            ApiResult.Ok(
                ConversationDetail(
                    id = "c1",
                    startedAt = "2026-09-29T08:00:00Z",
                    endedAt = "2026-09-29T08:10:00Z",
                    status = "closed",
                    segments = segments.toList(),
                ),
            )
        return ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1")),
            api,
            FakeTasks(emptyList()),
            FakeBookmarks(),
            FakeReview(),
            FakeAudio(),
            FakePlayer(),
            clock,
            info,
            FakeTags(),
            speech,
        )
    }

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `a kind shows a solid chip and a guess without a kind an outlined one`() {
        val state = viewModel(line(1, kind = "media"), line(2, kind = "call"), line(3, guess = "media")).state.value

        assertEquals(
            listOf(
                SpeechLabel(SpeechTag.Media, guess = false),
                SpeechLabel(SpeechTag.Call, guess = false),
                SpeechLabel(SpeechTag.Media, guess = true),
            ),
            state.paragraphs.map { it.speech },
        )
        assertTrue(state.speechAccess)
    }

    @Test
    fun `person, unsure and no kind show nothing, and the kind wins over a guess`() {
        val state =
            viewModel(
                line(1, kind = "person"),
                line(2, kind = "unsure"),
                line(3, guess = "unsure"),
                line(4, kind = "person", guess = "media"),
                line(5),
            ).state.value

        assertTrue(state.paragraphs.all { it.speech == null })
    }

    @Test
    fun `without speech-kind no chip, no access and no call`() {
        val vm = viewModel(line(1, kind = "media", guess = "media"), info = fakeInfo(ServerInfo.FEATURE_TAGS))

        vm.markKind(1, "call")
        vm.markOthers("media")

        assertFalse(vm.state.value.speechAccess)
        assertNull(
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        assertTrue(speech.calls.isEmpty())
    }

    @Test
    fun `a failed info read shows nothing`() {
        val vm = viewModel(line(1, kind = "media"), info = InfoClient { ApiResult.Failure(FailureKind.Server, "No.") })

        assertFalse(vm.state.value.speechAccess)
        assertNull(
            vm.state.value.paragraphs
                .single()
                .speech,
        )
    }

    @Test
    fun `a mark shows at once and keeps the server's answer`() {
        val gate = CompletableDeferred<Unit>()
        speech.gate = gate
        speech.segmentAnswer = ApiResult.Ok(line(1, kind = "media", marked = true))
        val vm = viewModel(line(1))

        vm.markKind(1, "media")

        assertEquals(
            SpeechLabel(SpeechTag.Media, guess = false),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        gate.complete(Unit)
        assertEquals(
            SpeechLabel(SpeechTag.Media, guess = false),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        assertEquals(listOf("segment 1 media"), speech.calls)
        assertNull(vm.state.value.speechNotice)
    }

    @Test
    fun `marking person removes the chip, and a line of the wearer can be marked`() {
        speech.segmentAnswer = ApiResult.Ok(line(1, kind = "person", marked = true))
        val vm = viewModel(line(1, kind = "media").copy(isUser = true))

        vm.markKind(1, "person")

        assertNull(
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        assertEquals(listOf("segment 1 person"), speech.calls)
    }

    @Test
    fun `a refused mark is taken back with a notice`() {
        val gate = CompletableDeferred<Unit>()
        speech.gate = gate
        speech.segmentAnswer = failure(FailureKind.Server)
        val vm = viewModel(line(1, guess = "call"))

        vm.markKind(1, "media")
        assertEquals(
            SpeechLabel(SpeechTag.Media, guess = false),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        gate.complete(Unit)

        assertEquals(
            SpeechLabel(SpeechTag.Call, guess = true),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        assertEquals(SpeechNotice.Failed, vm.state.value.speechNotice)
    }

    @Test
    fun `a refusal says why`() {
        val cases =
            mapOf(
                FailureKind.Forbidden to SpeechNotice.NeedsAdmin,
                FailureKind.NotFound to SpeechNotice.Gone,
                FailureKind.Unsupported to SpeechNotice.NeedsUpdate,
            )
        cases.forEach { (kind, notice) ->
            speech.segmentAnswer = failure(kind)
            val vm = viewModel(line(1))

            vm.markKind(1, "call")

            assertEquals(notice, vm.state.value.speechNotice)
        }
    }

    @Test
    fun `a notice goes when it is shown`() {
        speech.segmentAnswer = failure(FailureKind.Server)
        val vm = viewModel(line(1))
        vm.markKind(1, "call")

        vm.speechNoticeShown()

        assertNull(vm.state.value.speechNotice)
    }

    @Test
    fun `clearing waits for the server, which says what the line is now`() {
        val gate = CompletableDeferred<Unit>()
        speech.gate = gate
        speech.segmentAnswer = ApiResult.Ok(line(1, guess = "media"))
        val vm = viewModel(line(1, kind = "media", marked = true))

        vm.markKind(1, null)
        assertEquals(
            SpeechLabel(SpeechTag.Media, guess = false),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        gate.complete(Unit)

        assertEquals(
            SpeechLabel(SpeechTag.Media, guess = true),
            vm.state.value.paragraphs
                .single()
                .speech,
        )
        assertEquals(listOf("segment 1 null"), speech.calls)
    }

    @Test
    fun `marking the other voices reloads the conversation`() {
        val vm = viewModel(line(1), line(2))
        speech.conversationAnswer = ApiResult.Ok(2)
        api.detail =
            ApiResult.Ok((api.detail as ApiResult.Ok).value.copy(segments = listOf(line(1, "media"), line(2, "media"))))

        vm.markOthers("media")

        assertEquals(listOf("conversation c1 media"), speech.calls)
        assertEquals(
            2,
            vm.state.value.paragraphs
                .count { it.speech?.tag == SpeechTag.Media },
        )
        assertTrue(vm.state.value.speechAccess)
        assertNull(vm.state.value.speechNotice)
    }

    @Test
    fun `a refused bulk mark leaves the lines and says so`() {
        speech.conversationAnswer = failure(FailureKind.Forbidden)
        val vm = viewModel(line(1))

        vm.markOthers("person")

        assertEquals(SpeechNotice.NeedsAdmin, vm.state.value.speechNotice)
        assertNull(
            vm.state.value.paragraphs
                .single()
                .speech,
        )
    }
}
