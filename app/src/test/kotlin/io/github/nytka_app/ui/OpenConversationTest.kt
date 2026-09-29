package io.github.nytka_app.ui

import app.cash.turbine.test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hand-over NytkaNavHost uses: an id sent before the Conversations tab collects still arrives, once. */
class OpenConversationTest {
    @Test
    fun `an id sent while the tab is away arrives when it collects`() =
        runTest {
            val requests = Channel<String>(Channel.CONFLATED)
            requests.trySend("a")

            requests.receiveAsFlow().test {
                assertEquals("a", awaitItem())
                requests.trySend("b")
                assertEquals("b", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
