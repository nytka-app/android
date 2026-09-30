package io.github.nytka_app.ui.ask

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AskAnswer
import io.github.nytka_app.core.api.AskClient
import io.github.nytka_app.core.api.AskSource
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.NEEDS_UPDATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AskViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeAsk : AskClient {
        var answer: ApiResult<AskAnswer> = ApiResult.Ok(AskAnswer("", emptyList()))
        val asked = mutableListOf<String>()

        override suspend fun ask(question: String) = answer.also { asked += question }
    }

    private val api = FakeAsk()
    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private fun newViewModel() = AskViewModel(api, clock)

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered 500.")

    @Test
    fun `an answer maps its markers to the conversations its sources open`() {
        api.answer =
            ApiResult.Ok(
                AskAnswer(
                    "Anna [1], tea [2], gone [3], nothing [4].",
                    listOf(
                        AskSource(1, "conversation", "c1", "c1", "Coffee", "2026-09-28T10:00:00Z", "met &amp; Anna"),
                        AskSource(2, "memory", "m1", "c9", null, "2026-09-27T10:00:00Z", "likes tea"),
                        AskSource(3, "conversation", "c3", null, "Walk", "2026-09-26T10:00:00Z", "a walk"),
                        AskSource(4, "memory", "m2", null, null, "2026-09-25T10:00:00Z", "a fact"),
                    ),
                ),
            )
        val viewModel = newViewModel()

        viewModel.setQuestion("  Who?  ")
        viewModel.ask()

        val result = viewModel.state.value.result!!
        assertEquals(listOf("Who?"), api.asked)
        assertEquals(
            listOf(
                AnswerPart.Text("Anna "),
                AnswerPart.Cite(1, "c1"),
                AnswerPart.Text(", tea "),
                AnswerPart.Cite(2, "c9"),
                AnswerPart.Text(", gone "),
                AnswerPart.Cite(3, "c3"),
                AnswerPart.Text(", nothing "),
                AnswerPart.Cite(4, null),
                AnswerPart.Text("."),
            ),
            result.parts,
        )
        assertEquals(listOf("c1", "c9", "c3", null), result.sources.map { it.openId })
        assertEquals("met & Anna", result.sources[0].snippet)
        assertEquals("Yesterday", result.sources[0].date)
        assertFalse(viewModel.state.value.asking)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `failures say what went wrong in fixed sentences`() {
        val cases =
            mapOf(
                FailureKind.Unavailable to AskViewModel.NO_MODEL,
                FailureKind.Timeout to AskViewModel.TIMED_OUT,
                FailureKind.BadGateway to AskViewModel.MODEL_FAILED,
                FailureKind.Network to AskViewModel.UNREACHABLE,
                FailureKind.NotFound to NEEDS_UPDATE,
            )
        for ((kind, sentence) in cases) {
            api.answer = failure(kind)
            val viewModel = newViewModel()
            viewModel.setQuestion("q")
            viewModel.ask()

            assertEquals(sentence, viewModel.state.value.error)
            assertNull(viewModel.state.value.result)
            assertFalse(viewModel.state.value.asking)
        }
    }

    @Test
    fun `a blank question is not sent and a long one is cut`() {
        val viewModel = newViewModel()

        viewModel.setQuestion("   ")
        viewModel.ask()
        assertTrue(api.asked.isEmpty())
        assertFalse(viewModel.state.value.canAsk)

        viewModel.setQuestion("x".repeat(600))
        assertEquals(AskViewModel.MAX_QUESTION, viewModel.state.value.question.length)
    }

    @Test
    fun `a new question clears the old error and keeps the last answer until the next one arrives`() {
        api.answer = failure(FailureKind.Timeout)
        val viewModel = newViewModel()
        viewModel.setQuestion("q")
        viewModel.ask()
        api.answer = ApiResult.Ok(AskAnswer("Plain answer.", emptyList()))

        viewModel.ask()

        assertNull(viewModel.state.value.error)
        assertEquals(
            listOf(AnswerPart.Text("Plain answer.")),
            viewModel.state.value.result!!
                .parts,
        )
    }
}
