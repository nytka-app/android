package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AskAnswer
import io.github.nytka_app.core.api.AskClient
import io.github.nytka_app.core.api.AskSource
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.ask.AskTab
import io.github.nytka_app.ui.ask.AskViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class AskA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private class FakeAsk(
        private val answer: suspend () -> ApiResult<AskAnswer>,
    ) : AskClient {
        override suspend fun ask(question: String) = answer()
    }

    private fun answer() =
        ApiResult.Ok(
            AskAnswer(
                "Anna [1] likes tea [2], and you owe her a call [3].",
                listOf(
                    AskSource(1, "conversation", "c1", "c1", "Coffee with Anna", "2026-09-28T10:00:00Z", "met Anna"),
                    AskSource(2, "memory", "m1", "c9", null, "2026-09-27T10:00:00Z", "likes tea"),
                    AskSource(3, "memory", "m2", null, null, "2026-09-25T10:00:00Z", "a call is due"),
                ),
            ),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        question: String? = null,
        reply: suspend () -> ApiResult<AskAnswer> = { answer() },
    ) {
        val model = AskViewModel(FakeAsk(reply), clock)
        question?.let {
            model.setQuestion(it)
            model.ask()
        }
        compose.setScreen(fontScale, dark) {
            AskTab(onOpenConversation = {}, viewModel = model, people = listOf(Person("p1", "Anna")))
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        question: String? = null,
        reply: suspend () -> ApiResult<AskAnswer> = { answer() },
    ) {
        show(fontScale, dark, question, reply)
        compose.assertClean()
    }

    @Test
    fun `ask with the ideas at 1_0 in light`() = clean(1f)

    @Test
    fun `ask with the ideas at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `ask with the ideas at 1_3`() = clean(1.3f)

    @Test
    fun `ask with the ideas at 2_0`() = clean(2f)

    @Test
    fun `ask with the ideas at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `an answer with sources at 1_0`() = clean(1f, question = "What does Anna like?")

    @Test
    fun `an answer with sources at 1_0 in dark`() = clean(1f, dark = true, question = "What does Anna like?")

    @Test
    fun `an answer with sources at 1_3`() = clean(1.3f, question = "What does Anna like?")

    @Test
    fun `an answer with sources at 2_0`() = clean(2f, question = "What does Anna like?")

    @Test
    fun `an answer with no source at 2_0`() =
        clean(2f, question = "What does Anna like?", reply = { ApiResult.Ok(AskAnswer("Nothing.", emptyList())) })

    @Test
    fun `a failed question at 1_0`() =
        clean(1f, question = "What?", reply = { ApiResult.Failure(FailureKind.Network, "offline") })

    @Test
    fun `a failed question at 2_0`() =
        clean(2f, question = "What?", reply = { ApiResult.Failure(FailureKind.Timeout, "slow") })

    @Test
    fun `a question being asked at 2_0`() =
        clean(2f, question = "What?", reply = {
            CompletableDeferred<ApiResult<AskAnswer>>().await()
        })

    @Test
    fun `an answer reads in this order`() {
        show(1f, false, "What does Anna like?")
        assertEquals(
            listOf(
                "Ask about your conversations",
                "Ask [Button]",
                "What does Anna like?",
                "Anna [1] likes tea [2], and you owe her a call [3].",
                "Source 1 [Button]",
                "Source 2 [Button]",
                "Source 3",
                "Sources [heading]",
                "Source 1, Coffee with Anna, “met Anna”, Yesterday [Button]",
                "Source 2, “likes tea”, Sunday, 27 September [Button]",
                "Source 3, “a call is due”, Friday, 25 September",
            ),
            compose.spokenOrder(),
        )
    }
}
