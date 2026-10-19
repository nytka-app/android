package io.github.nytka_app.ui.people.ledger

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Ledger
import io.github.nytka_app.core.api.LedgerClient
import io.github.nytka_app.core.api.LedgerSide
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.fakeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LedgerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeLedger(
        var answer: ApiResult<Ledger>,
    ) : LedgerClient {
        val asked = mutableListOf<String>()

        override suspend fun ledger(personId: String): ApiResult<Ledger> {
            asked += personId
            return answer
        }
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private val ledger =
        Ledger(
            "p1",
            "Anna",
            owedByMe =
                LedgerSide(
                    open =
                        listOf(
                            NytkaTask(
                                "t1",
                                "c1",
                                "Bring jars",
                                conversationTitle = "Market",
                                conversationStartedAt = "2026-10-19T08:00:00Z",
                            ),
                        ),
                    done = listOf(NytkaTask("t2", "c1", "Returned the basket", done = true)),
                ),
            owedToMe = LedgerSide(open = listOf(NytkaTask("t3", null, "Send the contract"))),
        )
    private val api = FakeLedger(ApiResult.Ok(ledger))
    private var features = arrayOf(ServerInfo.FEATURE_LEDGER)

    private fun viewModel() = LedgerViewModel(SavedStateHandle(mapOf("id" to "p1")), api, fakeInfo(*features), clock)

    @Test
    fun `both sides show their open and done promises`() {
        val state = viewModel().state.value

        assertTrue(state.available)
        assertEquals(listOf("Bring jars"), state.owedByMe.open.map { it.text })
        assertEquals(
            "Market · Yesterday",
            state.owedByMe.open
                .single()
                .source,
        )
        assertEquals(listOf("Returned the basket"), state.owedByMe.done.map { it.text })
        assertEquals(listOf("Send the contract"), state.owedToMe.open.map { it.text })
        assertTrue(state.owedToMe.done.isEmpty())
        assertEquals(listOf("p1"), api.asked)
    }

    @Test
    fun `a promise made by hand has no conversation and no source`() {
        val entry =
            viewModel()
                .state.value.owedToMe.open
                .single()

        assertNull(entry.conversationId)
        assertEquals("", entry.source)
    }

    @Test
    fun `a server without the ledger feature is never asked`() {
        features = emptyArray()

        val state = viewModel().state.value

        assertFalse(state.available)
        assertNull(state.error)
        assertTrue(api.asked.isEmpty())
    }

    @Test
    fun `a 404 hides the section without an error`() {
        api.answer = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = viewModel().state.value

        assertFalse(state.available)
        assertNull(state.error)
    }

    @Test
    fun `another failure keeps the section and says why`() {
        api.answer = ApiResult.Failure(FailureKind.Forbidden, "Forbidden.")

        val state = viewModel().state.value

        assertTrue(state.available)
        assertEquals("The app needs an admin token.", state.error)
    }

    @Test
    fun `refresh reads the ledger again`() {
        val viewModel = viewModel()
        api.answer = ApiResult.Ok(ledger.copy(owedToMe = LedgerSide()))

        viewModel.refresh()

        assertTrue(viewModel.state.value.owedToMe.isEmpty)
        assertEquals(2, api.asked.size)
    }
}
