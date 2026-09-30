package io.github.nytka_app.pendant

import app.cash.turbine.test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePendantTest {
    private fun TestScope.pendant() =
        FakePendant(listOf(byteArrayOf(1), byteArrayOf(2)), backgroundScope) { testScheduler.currentTime }

    @Test
    fun `streams frames only while audio is on`() =
        runTest {
            val pendant = pendant()
            val received = mutableListOf<AudioFrame>()
            backgroundScope.launch { pendant.frames.collect { received += it } }
            pendant.connect("fake")
            runCurrent()

            advanceTimeBy(100)
            assertEquals(0, received.size)

            pendant.setAudio(true)
            advanceTimeBy(100)
            assertEquals(5, received.size)
            assertEquals(listOf<Byte>(1, 2, 1, 2, 1), received.map { it.payload.single() })

            pendant.setAudio(false)
            advanceTimeBy(100)
            assertEquals(5, received.size)
        }

    @Test
    fun `a dropped link stops audio and a reconnect resumes it`() =
        runTest {
            val pendant = pendant()
            pendant.connect("fake")
            pendant.setAudio(true)

            pendant.dropLink()
            assertEquals(PendantConnection.Disconnected, pendant.connection.value)
            assertFalse(pendant.audioEnabled)
            pendant.connect("fake")

            assertTrue(pendant.audioEnabled)
            assertTrue(pendant.connection.value is PendantConnection.Connected)
        }

    @Test
    fun `disconnect clears the audio intent`() =
        runTest {
            val pendant = pendant()
            pendant.connect("fake")
            pendant.setAudio(true)

            pendant.disconnect()
            pendant.connect("fake")

            assertFalse(pendant.audioEnabled)
        }

    @Test
    fun `records haptics and emits buttons`() =
        runTest {
            val pendant = pendant()
            pendant.connect("fake")

            pendant.buttons.test {
                pendant.press(ButtonEvent.DoubleTap)
                assertEquals(ButtonEvent.DoubleTap, awaitItem())
            }
            pendant.buzz(Haptic.Long)

            assertEquals(listOf(Haptic.Long), pendant.haptics)
        }

    @Test
    fun `a refused pendant never streams`() =
        runTest {
            val pendant = pendant()
            pendant.refuse("The pendant sends codec 20; Nytka needs Opus (21).")
            pendant.connect("fake")
            pendant.setAudio(true)

            assertTrue(pendant.connection.value is PendantConnection.Refused)
            assertFalse(pendant.audioEnabled)
        }

    @Test
    fun `button codes map to events`() {
        assertEquals(
            listOf(ButtonEvent.SingleTap, ButtonEvent.DoubleTap, ButtonEvent.Release, null),
            listOf(1, 2, 5, 3).map(ButtonEvent::fromCode),
        )
    }

    @Test
    fun `settings round trip through the fake ring after a connect`() =
        runTest {
            val pendant = pendant()
            assertEquals(SettingsSupport(), pendant.settings.support.value)

            pendant.connect("fake")
            runCurrent()

            assertEquals(SettingsSupport(led = true, gain = true), pendant.settings.support.value)
            assertEquals(SettingsValues(led = 50, gain = 6), pendant.settings.values.value)

            assertTrue(pendant.settings.setLed(80))
            assertTrue(pendant.settings.setGain(3))
            assertEquals(80, pendant.ring.led)
            assertEquals(3, pendant.ring.gain)

            pendant.dropLink()
            assertEquals(SettingsSupport(), pendant.settings.support.value)
            pendant.connect("fake")
            runCurrent()
            assertEquals(SettingsValues(led = 80, gain = 3), pendant.settings.values.value)
        }

    @Test
    fun `a ring without the feature bits offers no settings and takes no writes`() =
        runTest {
            val pendant = pendant()
            pendant.ring.features = 1L shl 6
            pendant.connect("fake")
            runCurrent()

            assertEquals(SettingsSupport(), pendant.settings.support.value)
            assertFalse(pendant.settings.setLed(80))
            assertTrue(pendant.ring.ledWrites.isEmpty())
        }

    @Test
    fun `settings reads that never answer do not hold up the connection or the storage`() =
        runTest {
            val pendant = pendant()
            pendant.ring.settingsHang = true

            pendant.connect("fake")
            runCurrent()

            assertTrue(pendant.connection.value is PendantConnection.Connected)
            assertEquals(StorageSupport.Supported, pendant.storage.support.value)
            assertEquals(SettingsValues(), pendant.settings.values.value)
        }
}
