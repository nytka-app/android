package io.github.nytka_app.capture

import io.github.nytka_app.FakeEventLog
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.pendant.ButtonEvent
import io.github.nytka_app.pendant.FakePendant
import io.github.nytka_app.pendant.Haptic
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CaptureControllerTest {
    private class FakeSink : FrameSink {
        val frames = mutableListOf<Pair<UUID, Long>>()
        var seals = 0

        override suspend fun add(
            session: UUID,
            seq: Long,
            capturedAtMs: Long,
            payload: ByteArray,
        ) {
            frames += session to seq
        }

        override suspend fun seal(includePartialStored: Boolean): Int = 1.also { seals++ }
    }

    private class FakeSettings(
        muted: Boolean,
    ) : CaptureSettings {
        val state = MutableStateFlow(muted)
        override val muted = state

        override suspend fun setMuted(muted: Boolean) {
            state.value = muted
        }
    }

    private class CountingPendant(
        private val inner: FakePendant,
    ) : Pendant by inner {
        var setAudioCalls = 0

        override suspend fun setAudio(enabled: Boolean) {
            setAudioCalls++
            inner.setAudio(enabled)
        }
    }

    private class Rig(
        val controller: CaptureController,
        val pendant: FakePendant,
        val sink: FakeSink,
        val settings: FakeSettings,
        val log: FakeEventLog,
    )

    private fun TestScope.rig(muted: Boolean = false): Rig {
        val now = { testScheduler.currentTime }
        val pendant = FakePendant(listOf(byteArrayOf(1)), backgroundScope, now)
        val sink = FakeSink()
        val settings = FakeSettings(muted)
        val log = FakeEventLog()
        val controller = CaptureController(pendant, sink, settings, backgroundScope, now, log = log)
        return Rig(controller, pendant, sink, settings, log)
    }

    @Test
    fun `records frames with a fresh session numbered from zero`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            runCurrent()
            advanceTimeBy(100)
            val first = rig.sink.frames.toList()

            rig.controller.stop()
            rig.sink.frames.clear()
            rig.controller.start("fake")
            runCurrent()
            advanceTimeBy(40)

            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), first.map { it.second })
            assertEquals(1, first.map { it.first }.distinct().size)
            assertEquals(listOf(0L, 1L), rig.sink.frames.map { it.second })
            assertNotEquals(first[0].first, rig.sink.frames[0].first)
        }

    @Test
    fun `seals every 30 seconds`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")

            advanceTimeBy(30_001)
            assertEquals(1, rig.sink.seals)
            advanceTimeBy(30_000)
            assertEquals(2, rig.sink.seals)
        }

    @Test
    fun `double tap mutes with one long buzz and nothing more enters the queue`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            runCurrent()
            advanceTimeBy(100)

            rig.pendant.press(ButtonEvent.DoubleTap)
            runCurrent()
            val count = rig.sink.frames.size
            advanceTimeBy(1_000)

            assertEquals(count, rig.sink.frames.size)
            assertEquals(listOf(Haptic.Long), rig.pendant.haptics)
            assertTrue(rig.settings.state.value)
            assertFalse(rig.pendant.audioEnabled)
        }

    @Test
    fun `a second double tap goes live with two short buzzes`() =
        runTest {
            val rig = rig(muted = true)
            rig.controller.start("fake")
            runCurrent()

            rig.pendant.press(ButtonEvent.DoubleTap)
            advanceTimeBy(300)

            assertEquals(listOf(Haptic.Short, Haptic.Short), rig.pendant.haptics)
            assertTrue(rig.pendant.audioEnabled)
            advanceTimeBy(100)
            assertTrue(rig.sink.frames.isNotEmpty())
        }

    @Test
    fun `reconnect stays muted`() =
        runTest {
            val rig = rig(muted = true)
            rig.controller.start("fake")
            runCurrent()

            rig.pendant.dropLink()
            runCurrent()
            rig.pendant.connect("fake")
            advanceTimeBy(1_000)

            assertFalse(rig.pendant.audioEnabled)
            assertTrue(rig.sink.frames.isEmpty())
            assertTrue(rig.pendant.haptics.isEmpty())
        }

    @Test
    fun `reconnect while unmuted resumes audio without the controller asking again`() =
        runTest {
            val rig = rig()
            val calls = CountingPendant(rig.pendant)
            val controller =
                CaptureController(
                    calls,
                    rig.sink,
                    rig.settings,
                    backgroundScope,
                    { testScheduler.currentTime },
                    log = rig.log,
                )
            controller.start("fake")
            runCurrent()
            assertEquals(1, calls.setAudioCalls) // before the link exists; Connected changes nothing

            // The collector never runs in between, so StateFlow conflates Connected to Connected
            // and the controller is not told: the pendant must resume audio by itself.
            rig.pendant.dropLink()
            assertFalse(rig.pendant.audioEnabled)
            rig.pendant.connect("fake")
            advanceTimeBy(100)

            assertTrue(rig.pendant.audioEnabled)
            assertTrue(rig.sink.frames.isNotEmpty())
            assertEquals(1, calls.setAudioCalls) // the pendant resumed on its own
        }

    @Test
    fun `the audio intent comes from the persisted mute setting before the pendant connects`() =
        runTest {
            val rig = rig(muted = true)
            val order = mutableListOf<String>()
            val ordered =
                object : Pendant by rig.pendant {
                    override suspend fun setAudio(enabled: Boolean) {
                        order += "audio $enabled"
                        rig.pendant.setAudio(enabled)
                    }

                    override fun connect(address: String) {
                        order += "connect"
                        rig.pendant.connect(address)
                    }
                }
            val controller =
                CaptureController(
                    ordered,
                    rig.sink,
                    rig.settings,
                    backgroundScope,
                    { testScheduler.currentTime },
                    log = rig.log,
                )

            controller.start("fake")
            runCurrent()

            assertEquals(listOf("audio false", "connect"), order)
            assertFalse(rig.pendant.audioEnabled)
        }

    @Test
    fun `an unmuted start subscribes as the link comes up`() =
        runTest {
            val rig = rig(muted = false)
            rig.controller.start("fake")
            runCurrent()

            assertTrue(rig.pendant.audioEnabled)
        }

    @Test
    fun `mute changes reach the mute log once each, whoever made them`() =
        runTest {
            val rig = rig()
            val muteSink = MuteLogRecorderTest.FakeMuteSink()
            val recorder = MuteLogRecorder(muteSink, { testScheduler.currentTime })
            val controller =
                CaptureController(
                    rig.pendant,
                    rig.sink,
                    rig.settings,
                    backgroundScope,
                    { testScheduler.currentTime },
                    log = rig.log,
                    muteLog = recorder,
                )
            controller.start("fake")
            runCurrent()
            assertTrue(muteSink.changes.isEmpty()) // unmuted at start, nothing to log

            advanceTimeBy(1_000)
            rig.pendant.press(ButtonEvent.DoubleTap)
            runCurrent()
            advanceTimeBy(4_000)
            controller.setMuted(false, MuteSource.App)
            runCurrent()

            assertEquals(listOf(1_000L to true, 5_000L to false), muteSink.changes.map { it.atMs to it.muted })
        }

    @Test
    fun `every mute change is logged with who asked for it`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            runCurrent()

            rig.pendant.press(ButtonEvent.DoubleTap)
            runCurrent()
            rig.controller.setMuted(false, MuteSource.Notification)
            rig.controller.setMuted(true, MuteSource.App)

            assertEquals(
                listOf("muted by pendant double-tap", "unmuted by notification action", "muted by app UI"),
                rig.log.messages.filter { it.contains("muted") },
            )
        }

    @Test
    fun `a battery level is logged once per change`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            runCurrent() // the fake reports 82 when it connects

            rig.pendant.setBattery(82) // no change
            rig.pendant.setBattery(81)
            runCurrent()
            rig.pendant.setBattery(81) // no change
            rig.pendant.setBattery(80)
            runCurrent()

            assertEquals(
                listOf("pendant battery 82%", "pendant battery 81%", "pendant battery 80%"),
                rig.log.messages.filter { it.startsWith("pendant battery") },
            )
        }

    @Test
    fun `a refused pendant records nothing`() =
        runTest {
            val rig = rig()
            rig.pendant.refuse("The pendant sends audio codec 20.")
            rig.controller.start("fake")
            advanceTimeBy(1_000)

            assertTrue(rig.controller.status.value.connection is PendantConnection.Refused)
            assertTrue(rig.sink.frames.isEmpty())
        }

    @Test
    fun `stop seals what is left and lets the pendant go`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            advanceTimeBy(100)

            rig.controller.stop()

            assertEquals(1, rig.sink.seals)
            assertEquals(PendantConnection.Disconnected, rig.pendant.connection.value)
            assertFalse(rig.controller.status.value.running)
        }

    @Test
    fun `disconnected since marks when the pendant left`() =
        runTest {
            val rig = rig()
            rig.controller.start("fake")
            runCurrent()
            assertNull(rig.controller.status.value.disconnectedSinceMs)

            advanceTimeBy(1_000)
            rig.pendant.dropLink()
            runCurrent()
            assertEquals(1_000L, rig.controller.status.value.disconnectedSinceMs)

            rig.pendant.connect("fake")
            runCurrent()
            assertNull(rig.controller.status.value.disconnectedSinceMs)
        }
}
