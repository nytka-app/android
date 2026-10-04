package io.github.nytka_app.pendant

import android.bluetooth.BluetoothAdapter
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
    fun `a failed attempt is tried once more and each failure is reported`() {
        val failures = mutableListOf<Int>()
        var attempts = 0

        val done =
            retrying(2, onFailed = { failures += it }) {
                attempts++
                attempts == 2 // the second try works
            }

        assertTrue(done)
        assertEquals(2, attempts)
        assertEquals(listOf(1), failures)
    }

    @Test
    fun `an attempt that fails every time gives up after the last one`() {
        val failures = mutableListOf<Int>()
        var attempts = 0

        val done =
            retrying(2, onFailed = { failures += it }) {
                attempts++
                false
            }

        assertFalse(done)
        assertEquals(2, attempts)
        assertEquals(listOf(1, 2), failures)
    }

    @Test
    fun `an attempt that works is not repeated`() {
        var attempts = 0

        val done =
            retrying(2) {
                attempts++
                true
            }

        assertTrue(done)
        assertEquals(1, attempts)
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

    /** A link that has already delivered a battery notification, so its silence can be judged. */
    private fun judged() = LinkWatchdog().also { it.pulseArrived() }

    /**
     * The watch loop over a live link: a tick every second and the pendant's battery notification every 5 s, so the
     * liveness clock is never more than 5 s old. Returns what the watchdog wanted, with the time it wanted it.
     */
    private fun LinkWatchdog.overALiveLink(
        lastAudioAtMs: Long,
        untilMs: Long,
    ): List<Pair<Long, WatchdogAction>> =
        (lastAudioAtMs..untilMs step 1_000).mapNotNull { t ->
            if (t % 5_000 == 0L) pulseArrived()
            val action = check(t, lastAudioAtMs, t - t % 5_000)
            (t to action).takeIf { action != WatchdogAction.None }
        }

    @Test
    fun `audio that stays quiet on a live link is noted once and resubscribed slowly, never reconnected`() {
        val actions = LinkWatchdog().overALiveLink(lastAudioAtMs = 0, untilMs = 3_600_000)

        assertEquals(WatchdogAction.AudioIdle(2_000), actions.first().second)
        assertEquals(
            listOf(2_000L, 60_000L, 180_000L, 420_000L, 900_000L, 1_500_000L, 2_100_000L, 2_700_000L, 3_300_000L),
            actions.map { it.first },
        )
        val waits = actions.drop(1).map { (it.second as WatchdogAction.Resubscribe).nextMs }
        assertEquals(listOf(120_000L, 240_000L, 480_000L, 600_000L, 600_000L), waits.take(5))
        assertTrue(actions.none { it.second is WatchdogAction.Escalate })
    }

    @Test
    fun `audio idle is noted once per silence`() {
        val watchdog = LinkWatchdog()

        assertEquals(WatchdogAction.None, watchdog.check(1_999, 0, 0))
        assertEquals(WatchdogAction.AudioIdle(2_000), watchdog.check(2_000, 0, 0))
        assertEquals(WatchdogAction.None, watchdog.check(3_000, 0, 0))
        assertEquals(WatchdogAction.None, watchdog.check(10_000, 10_000, 10_000)) // audio came back at 10 s
        assertEquals(WatchdogAction.AudioIdle(2_000), watchdog.check(12_000, 10_000, 10_000))
    }

    @Test
    fun `audio arriving starts the resubscribe backoff over at 60 seconds`() {
        val watchdog = LinkWatchdog()
        assertEquals(WatchdogAction.AudioIdle(2_000), watchdog.check(2_000, 0, 0))
        assertEquals(WatchdogAction.Resubscribe(60_000, 120_000), watchdog.check(60_000, 0, 60_000))
        assertEquals(WatchdogAction.Resubscribe(180_000, 240_000), watchdog.check(180_000, 0, 180_000))

        assertEquals(WatchdogAction.None, watchdog.check(200_000, 200_000, 200_000)) // audio arrived at 200 s
        assertEquals(WatchdogAction.AudioIdle(2_000), watchdog.check(202_000, 200_000, 200_000))
        assertEquals(WatchdogAction.None, watchdog.check(259_999, 200_000, 255_000))
        assertEquals(WatchdogAction.Resubscribe(60_000, 120_000), watchdog.check(260_000, 200_000, 260_000))
    }

    @Test
    fun `reconnect only after 20 seconds without a notification of any kind`() {
        val watchdog = judged()
        val escalations = (0L..19_000L step 1_000).filter { watchdog.check(it, 0, 0) is WatchdogAction.Escalate }

        assertTrue(escalations.isEmpty())
        assertEquals(WatchdogAction.Escalate(20_000), watchdog.check(20_000, 0, 0))
    }

    @Test
    fun `reconnect at most once per 60 seconds`() {
        val watchdog = judged()
        assertEquals(WatchdogAction.Escalate(20_000), watchdog.check(20_000, 0, 0))

        // The reconnect brought nothing back: still quiet, so inside the cooldown, and a dead link is not resubscribed.
        assertEquals(WatchdogAction.None, watchdog.check(70_000, 0, 0))
        assertEquals(WatchdogAction.None, watchdog.check(79_999, 0, 0))
        assertEquals(WatchdogAction.Escalate(80_000), watchdog.check(80_000, 0, 0))
    }

    @Test
    fun `the cooldown counts time only, a notification after the reconnect does not lift it`() {
        val watchdog = judged()
        assertEquals(WatchdogAction.Escalate(20_000), watchdog.check(20_000, 0, 0))

        // The reconnect delivers one notification, during setUp, and the link goes quiet again.
        watchdog.linkUp()
        watchdog.pulseArrived()
        assertEquals(WatchdogAction.None, watchdog.check(45_000, 25_000, 25_000)) // quiet for 20 s, but 25 s since
        assertEquals(WatchdogAction.None, watchdog.check(79_999, 25_000, 25_000))
        assertEquals(WatchdogAction.Escalate(55_000), watchdog.check(80_000, 25_000, 25_000))
    }

    @Test
    fun `a link that never delivered a battery or button notification is never judged dead`() {
        val watchdog = LinkWatchdog() // the battery subscription may never have taken

        val escalations = (0L..600_000L step 1_000).filter { watchdog.check(it, 0, 0) is WatchdogAction.Escalate }
        assertTrue(escalations.isEmpty())

        watchdog.pulseArrived()
        assertEquals(WatchdogAction.Escalate(601_000), watchdog.check(601_000, 0, 0))
    }

    @Test
    fun `a new connection has to deliver its own pulse before its silence is read as death`() {
        val watchdog = judged()

        watchdog.linkUp()
        assertEquals(WatchdogAction.None, watchdog.check(100_000, 0, 0))

        watchdog.pulseArrived()
        assertEquals(WatchdogAction.Escalate(100_000), watchdog.check(100_000, 0, 0))
    }

    @Test
    fun `a muted pendant is never checked so it never escalates`() {
        // OmiPendant.watch calls check only while AudioIntent.wanted; the intent itself is what mutes.
        val intent = AudioIntent()
        val watchdog = LinkWatchdog()
        intent.set(false)
        val actions =
            (0L..600_000L step 1_000).map {
                if (intent.wanted) watchdog.check(it, 0, 0) else WatchdogAction.None
            }
        assertTrue(actions.all { it == WatchdogAction.None })
    }

    @Test
    fun `audio back after two seconds of silence reports the silence, sooner reports nothing`() {
        val resume = ResumeDetector()
        resume.switchedOn(1_000)

        assertNull(resume.arrived(1_040)) // the first notification of a healthy start
        assertNull(resume.arrived(2_039)) // 999 ms
        assertNull(resume.arrived(4_038)) // 1999 ms
        assertEquals(2_000L, resume.arrived(6_038))
    }

    @Test
    fun `a silence is reported once and the next notifications are quiet`() {
        val resume = ResumeDetector()
        resume.switchedOn(0)

        assertEquals(30_012L, resume.arrived(30_012))
        assertNull(resume.arrived(30_032))
        assertNull(resume.arrived(30_052))
    }

    @Test
    fun `a stall that began before the first notification is measured from switching audio on`() {
        val resume = ResumeDetector()
        resume.switchedOn(5_000)

        assertEquals(34_000L, resume.arrived(39_000))
    }

    @Test
    fun `a mute is no silence because unmuting switches audio on again`() {
        val resume = ResumeDetector()
        resume.switchedOn(0)
        assertNull(resume.arrived(40))

        // muted for ten minutes: no notifications, no call
        resume.switchedOn(600_040)

        assertNull(resume.arrived(600_080))
    }

    @Test
    fun `notifications nobody switched on measure from the first one`() {
        val resume = ResumeDetector()

        assertNull(resume.arrived(5_000)) // a subscription that outlived the app: nothing to compare with yet
        assertNull(resume.arrived(5_020))
        assertEquals(4_000L, resume.arrived(9_020))
    }

    @Test
    fun `a read or write that timed out leaves the client stuck until a late callback frees it`() {
        val busy = BusyClient()
        assertFalse(busy.stuck)
        busy.timedOut(OperationKind.Read)
        assertTrue(busy.stuck) // every later read and write fails at once, as Android refuses them
        assertFalse(busy.lateCallback(OperationKind.Mtu)) // not a gated callback: still stuck
        assertTrue(busy.stuck)
        assertTrue(busy.lateCallback(OperationKind.Read))
        assertFalse(busy.stuck)
        assertFalse(busy.lateCallback(OperationKind.Read)) // nothing left to free
    }

    @Test
    fun `MTU and service discovery timeouts do not stick the client`() {
        val busy = BusyClient()
        busy.timedOut(OperationKind.Mtu)
        busy.timedOut(OperationKind.Services)
        assertFalse(busy.stuck)
        busy.timedOut(OperationKind.DescriptorWrite)
        assertTrue(busy.stuck)
    }

    @Test
    fun `a new connection or a closed client starts unstuck`() {
        val busy = BusyClient()
        busy.timedOut(OperationKind.Write)
        busy.reset()
        assertFalse(busy.stuck)
    }

    @Test
    fun `a stuck setUp reconnects once and carries on the second time in a row`() {
        val stuckSetUps = FailureStreak(2) // what OmiPendant keeps
        assertFalse(stuckSetUps.failed()) // first: reconnect
        assertTrue(stuckSetUps.failed()) // second in a row: carry on degraded
        stuckSetUps.succeeded() // a clean setUp
        assertFalse(stuckSetUps.failed())
    }

    @Test
    fun `the adapter coming on reopens, going off drops the client, anything else is ignored`() {
        assertEquals(AdapterAction.Reopen, adapterAction(BluetoothAdapter.STATE_ON))
        assertEquals(AdapterAction.Drop, adapterAction(BluetoothAdapter.STATE_TURNING_OFF))
        assertEquals(AdapterAction.Drop, adapterAction(BluetoothAdapter.STATE_OFF))
        assertEquals(AdapterAction.None, adapterAction(BluetoothAdapter.STATE_TURNING_ON))
        assertEquals(AdapterAction.None, adapterAction(BluetoothAdapter.ERROR))
    }
}
