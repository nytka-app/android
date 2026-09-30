package io.github.nytka_app.capture

import io.github.nytka_app.pendant.FakePendant
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendantSettingsControllerTest {
    private class Rig(
        scope: TestScope,
    ) {
        val pendant = FakePendant(emptyList(), scope.backgroundScope) { scope.testScheduler.currentTime }
        val controller = PendantSettingsController(pendant, scope.backgroundScope)
    }

    private suspend fun TestScope.connected(): Rig {
        val rig = Rig(this)
        rig.pendant.connect("fake")
        runCurrent()
        return rig
    }

    @Test
    fun `values appear once the pendant is connected and read`() =
        runTest {
            val rig = Rig(this)
            assertEquals(PendantSettingsState(), rig.controller.state.value)

            rig.pendant.connect("fake")
            runCurrent()

            assertEquals(PendantSettingsState(led = 50, gain = 6), rig.controller.state.value)
        }

    @Test
    fun `a pendant without the feature bits shows nothing`() =
        runTest {
            val rig = Rig(this)
            rig.pendant.ring.features = 1L shl 6

            rig.pendant.connect("fake")
            runCurrent()

            assertNull(rig.controller.state.value.led)
            assertNull(rig.controller.state.value.gain)
        }

    @Test
    fun `a lost link hides the values`() =
        runTest {
            val rig = connected()

            rig.pendant.dropLink()
            runCurrent()

            assertEquals(PendantConnection.Disconnected, rig.pendant.connection.value)
            assertNull(rig.controller.state.value.led)
        }

    @Test
    fun `the first commit is written at once`() =
        runTest {
            val rig = connected()

            rig.controller.commitLed(70)
            runCurrent()

            assertEquals(listOf(70), rig.pendant.ring.ledWrites)
            assertEquals(70, rig.controller.state.value.led)
        }

    @Test
    fun `commits inside two seconds coalesce into one later write of the last value`() =
        runTest {
            val rig = connected()

            rig.controller.commitLed(70)
            runCurrent()
            rig.controller.commitLed(10)
            advanceTimeBy(500)
            rig.controller.commitLed(20)
            advanceTimeBy(1_000)
            assertEquals(listOf(70), rig.pendant.ring.ledWrites)

            advanceTimeBy(600)
            runCurrent()
            assertEquals(listOf(70, 20), rig.pendant.ring.ledWrites)
        }

    @Test
    fun `writes of one control are at least two seconds apart`() =
        runTest {
            val rig = connected()
            val start = testScheduler.currentTime

            rig.controller.commitGain(2)
            runCurrent()
            rig.controller.commitGain(4)
            advanceTimeBy(1_999)
            assertEquals(listOf(2), rig.pendant.ring.gainWrites)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(2, 4), rig.pendant.ring.gainWrites)
            assertTrue(testScheduler.currentTime - start >= 2_000)
        }

    @Test
    fun `the two controls do not hold each other back`() =
        runTest {
            val rig = connected()

            rig.controller.commitLed(30)
            rig.controller.commitGain(5)
            runCurrent()

            assertEquals(listOf(30), rig.pendant.ring.ledWrites)
            assertEquals(listOf(5), rig.pendant.ring.gainWrites)
        }

    @Test
    fun `a commit after a quiet spell is written at once`() =
        runTest {
            val rig = connected()
            rig.controller.commitLed(30)
            advanceTimeBy(2_500)

            rig.controller.commitLed(40)
            runCurrent()

            assertEquals(listOf(30, 40), rig.pendant.ring.ledWrites)
        }

    @Test
    fun `a failed write is reported, keeps the old value and the next write still goes out`() =
        runTest {
            val rig = connected()
            rig.pendant.ring.settingsFail = true

            rig.controller.commitLed(90)
            runCurrent()

            assertEquals(1, rig.controller.state.value.ledFailures)
            assertEquals(0, rig.controller.state.value.gainFailures)
            assertEquals(PendantSettingsController.LED_FAILED, rig.controller.state.value.error)
            assertEquals(50, rig.controller.state.value.led)

            rig.pendant.ring.settingsFail = false
            rig.controller.commitLed(60)
            advanceTimeBy(2_100)

            assertEquals(listOf(60), rig.pendant.ring.ledWrites)
            assertNull(rig.controller.state.value.error)
            assertEquals(60, rig.controller.state.value.led)
        }

    @Test
    fun `a commit with no support writes nothing`() =
        runTest {
            val rig = Rig(this)
            rig.pendant.ring.features = 0

            rig.pendant.connect("fake")
            runCurrent()
            rig.controller.commitGain(1)
            runCurrent()

            assertTrue(
                rig.pendant.ring.gainWrites
                    .isEmpty(),
            )
        }

    @Test
    fun `a commit exactly when the wait ends is written`() =
        runTest {
            val rig = connected()
            rig.controller.commitLed(30)
            runCurrent()

            advanceTimeBy(1_999)
            rig.controller.commitLed(40)
            advanceTimeBy(1) // the wait ends at this instant
            rig.controller.commitLed(50)
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(
                30,
                rig.pendant.ring.ledWrites
                    .first(),
            )
            assertEquals(
                50,
                rig.pendant.ring.ledWrites
                    .last(),
            )
            assertEquals(50, rig.pendant.ring.led)
        }

    @Test
    fun `a commit while a write is running is written after it`() =
        runTest {
            val rig = connected()
            rig.pendant.ring.settingsDelayMs = 500

            rig.controller.commitGain(2)
            advanceTimeBy(100)
            rig.controller.commitGain(5)
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(listOf(2, 5), rig.pendant.ring.gainWrites)
        }

    @Test
    fun `a failed led write does not reset the gain slider`() =
        runTest {
            val rig = connected()
            rig.pendant.ring.settingsFail = true

            rig.controller.commitLed(90)
            runCurrent()

            assertEquals(1, rig.controller.state.value.ledFailures)
            assertEquals(0, rig.controller.state.value.gainFailures)
        }
}
