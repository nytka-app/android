package io.github.nytka_app.pendant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ReconnectTest {
    @Test
    fun `audio intent survives everything except an explicit clear`() {
        val intent = AudioIntent()
        assertFalse(intent.wanted)
        intent.set(true)
        assertTrue(intent.wanted) // a lost link does not touch it
        intent.set(false)
        assertFalse(intent.wanted) // muted: a reconnect must not resubscribe
        intent.set(true)
        intent.clear()
        assertFalse(intent.wanted)
    }

    @Test
    fun `a connect attempt that never completes is retried and re-armed`() =
        runTest {
            var retries = 0
            lateinit var timeout: ConnectTimeout
            timeout =
                ConnectTimeout(backgroundScope, 30_000, isConnecting = { true }) {
                    retries++
                    timeout.arm() // what open() does
                }
            timeout.arm()

            advanceTimeBy(29_999)
            assertEquals(0, retries)
            advanceTimeBy(2)
            assertEquals(1, retries)
            advanceTimeBy(30_000)
            assertEquals(2, retries)
        }

    @Test
    fun `no retry once connected or cancelled`() =
        runTest {
            var retries = 0
            var connecting = true
            val timeout = ConnectTimeout(backgroundScope, 30_000, isConnecting = { connecting }) { retries++ }
            timeout.arm()
            connecting = false
            advanceTimeBy(31_000)
            assertEquals(0, retries)

            connecting = true
            timeout.arm()
            timeout.cancel()
            advanceTimeBy(31_000)
            assertEquals(0, retries)
        }

    @Test
    fun `cancelling after a connect at 25 seconds stops the 30 second retry`() =
        runTest {
            var retries = 0
            val timeout = ConnectTimeout(backgroundScope, 30_000, isConnecting = { true }) { retries++ }
            timeout.arm()
            advanceTimeBy(25_000)
            timeout.cancel() // STATE_CONNECTED or disconnect()
            advanceTimeBy(60_000)
            assertEquals(0, retries)
        }

    @Test
    fun `a late callback does not complete a different operation`() {
        val uuid = UUID.randomUUID()
        val op = PendingOperation(OperationKind.DescriptorWrite, uuid)

        assertFalse(op.complete(OperationKind.Read, uuid, byteArrayOf()))
        assertFalse(op.complete(OperationKind.DescriptorWrite, UUID.randomUUID(), byteArrayOf()))
        assertFalse(op.done.isCompleted)

        assertTrue(op.complete(OperationKind.DescriptorWrite, uuid, null))
        assertNull(op.done.getCompleted())
    }

    @Test
    fun `addresses are logged by their tail only`() {
        assertEquals("…EE:FF", redactAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a dead Bluetooth stack fails the call instead of throwing`() {
        val dead = RuntimeException(IllegalStateException("android.os.DeadObjectException"))
        var reported: RuntimeException? = null

        val started = gattCall(false, onError = { reported = it }) { throw dead }

        assertFalse(started) // the same outcome as writeDescriptor returning false
        assertSame(dead, reported)
    }

    @Test
    fun `a revoked permission fails the call instead of throwing`() {
        assertFalse(gattCall(false) { throw SecurityException("BLUETOOTH_CONNECT") })
    }

    @Test
    fun `a call that works keeps its result`() {
        assertTrue(gattCall(false) { true })
    }

    @Test
    fun `cancellation passes through the guard`() {
        try {
            gattCall(false) { throw CancellationException("scope cancelled") }
            fail("cancellation must not be swallowed")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `three failures in a row escalate and a success starts the count over`() {
        val streak = FailureStreak(3)
        assertFalse(streak.failed())
        assertFalse(streak.failed())
        streak.succeeded()
        assertFalse(streak.failed())
        assertFalse(streak.failed())
        assertTrue(streak.failed())
    }

    private fun SilenceWatchdog.resubscribeTimes(
        lastAudioAtMs: Long,
        untilMs: Long,
    ): List<Pair<Long, WatchdogAction.Resubscribe>> =
        (lastAudioAtMs..untilMs step 1_000).mapNotNull { t ->
            (check(t, lastAudioAtMs) as? WatchdogAction.Resubscribe)?.let { t to it }
        }

    @Test
    fun `silence backs off 4 8 16 then 30 seconds`() {
        val hits = SilenceWatchdog(escalateAfterMs = Long.MAX_VALUE).resubscribeTimes(0, 130_000)
        assertEquals(listOf(4_000L, 12_000L, 28_000L, 58_000L, 88_000L, 118_000L), hits.map { it.first })
        assertEquals(listOf(8_000L, 16_000L, 30_000L, 30_000L), hits.take(4).map { it.second.nextMs })
        assertEquals(4_000, hits.first().second.silentMs)
    }

    @Test
    fun `audio restarts the backoff at 4 seconds`() {
        val watchdog = SilenceWatchdog()
        watchdog.check(0, 0)
        assertTrue(watchdog.check(4_000, 0) is WatchdogAction.Resubscribe)
        assertTrue(watchdog.check(12_000, 0) is WatchdogAction.Resubscribe)
        assertEquals(WatchdogAction.None, watchdog.check(20_000, 20_000)) // audio arrived at 20 s
        assertEquals(WatchdogAction.None, watchdog.check(23_999, 20_000))
        assertEquals(WatchdogAction.Resubscribe(4_000, 8_000), watchdog.check(24_000, 20_000))
    }

    @Test
    fun `reconnect only after 120 seconds of continuous silence`() {
        val watchdog = SilenceWatchdog()
        val escalations = (0L..119_000L step 1_000).filter { watchdog.check(it, 0) is WatchdogAction.Escalate }
        assertTrue(escalations.isEmpty())
        assertEquals(WatchdogAction.Escalate(120_000), watchdog.check(120_000, 0))
    }

    @Test
    fun `reconnect at most once per five minutes`() {
        val watchdog = SilenceWatchdog()
        assertEquals(WatchdogAction.Escalate(120_000), watchdog.check(120_000, 0))
        // the reconnect resets lastAudioAtMs; still silent 120 s later, but inside the cooldown
        assertTrue(watchdog.check(300_000, 180_000) !is WatchdogAction.Escalate)
        assertTrue(watchdog.check(419_999, 180_000) !is WatchdogAction.Escalate)
        assertEquals(WatchdogAction.Escalate(240_000), watchdog.check(420_000, 180_000))
    }

    @Test
    fun `audio after a reconnect lifts the cap`() {
        val watchdog = SilenceWatchdog()
        assertEquals(WatchdogAction.Escalate(120_000), watchdog.check(120_000, 0))
        watchdog.audioArrived() // the reconnect brought audio back
        watchdog.check(130_000, 125_000)
        // silent again for 120 s, well inside 5 minutes of the first escalation
        assertEquals(WatchdogAction.Escalate(120_000), watchdog.check(245_000, 125_000))
    }

    @Test
    fun `a muted pendant is never checked so it never escalates`() {
        // OmiPendant.watch calls check only while AudioIntent.wanted; the intent itself is what mutes.
        val intent = AudioIntent()
        val watchdog = SilenceWatchdog()
        intent.set(false)
        val actions =
            (0L..600_000L step 1_000).map {
                if (intent.wanted) {
                    watchdog.check(
                        it,
                        0,
                    )
                } else {
                    WatchdogAction.None
                }
            }
        assertTrue(actions.all { it == WatchdogAction.None })
    }
}
