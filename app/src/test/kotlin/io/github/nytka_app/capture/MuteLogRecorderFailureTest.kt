package io.github.nytka_app.capture

import io.github.nytka_app.FakeEventLog
import io.github.nytka_app.core.ring.MuteChange
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for a real `SQLException`, which the mute log must survive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MuteLogRecorderFailureTest {
    @Test
    fun `a refused write is retried with the change's own time until it lands`() =
        runTest {
            val sink = MuteLogRecorderTest.FakeMuteSink(failures = 2)
            val recorder = MuteLogRecorder(sink, { testScheduler.currentTime + 500 }, FakeEventLog(), retryMs = 1_000)

            backgroundScope.launch { recorder.record(true) }
            runCurrent()
            assertTrue(sink.changes.isEmpty())
            advanceTimeBy(3_500)
            runCurrent()

            assertEquals(listOf(MuteChange(500, true)), sink.changes)
        }
}
