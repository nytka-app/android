package io.github.nytka_app.capture

import io.github.nytka_app.FakeEventLog
import io.github.nytka_app.pendant.Capabilities
import io.github.nytka_app.pendant.FakePendant
import io.github.nytka_app.pendant.OmiDriver
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** The gate before capture: a recorder whose codec the server does not list is refused (server#246). */
class CaptureControllerCodecTest {
    private class Rig(
        val controller: CaptureController,
        val pendant: FakePendant,
        val sink: FakeSink,
    )

    private fun TestScope.rig(
        capabilities: Capabilities = OmiDriver.capabilities,
        serverReads: suspend (Byte) -> Boolean,
    ): Rig {
        val now = { testScheduler.currentTime }
        val pendant = FakePendant(listOf(byteArrayOf(1)), backgroundScope, capabilities = capabilities, now = now)
        val sink = FakeSink()
        val controller =
            CaptureController(
                pendant,
                sink,
                FakeSettings(false),
                backgroundScope,
                now,
                log = FakeEventLog(),
                zone = { ZoneOffset.UTC },
                serverReads = serverReads,
            )
        return Rig(controller, pendant, sink)
    }

    @Test
    fun `a recorder whose codec the server cannot read is refused and records nothing`() =
        runTest {
            val rig = rig(OmiDriver.capabilities.copy(codec = 40)) { it == 21.toByte() }
            rig.controller.start("fake")
            runCurrent()
            advanceTimeBy(100)

            val connection = rig.controller.status.value.connection
            assertTrue(connection is PendantConnection.Refused)
            assertEquals(
                "This server cannot read this recorder's audio yet. Update the server.",
                (connection as PendantConnection.Refused).reason,
            )
            assertFalse(rig.pendant.audioEnabled)
            assertTrue(rig.sink.frames.isEmpty())
            assertFalse(rig.controller.status.value.recording)
        }

    @Test
    fun `a recorder whose codec the server lists records as before`() =
        runTest {
            val rig = rig { it == 21.toByte() }
            rig.controller.start("fake")
            runCurrent()
            advanceTimeBy(100)

            assertTrue(rig.controller.status.value.connection is PendantConnection.Connected)
            assertTrue(rig.sink.frames.isNotEmpty())
        }

    @Test
    fun `a refused recorder is checked again when the server is updated`() =
        runTest {
            var listed = false
            val rig = rig(OmiDriver.capabilities.copy(codec = 40)) { listed }
            rig.controller.start("fake")
            runCurrent()
            assertTrue(rig.controller.status.value.connection is PendantConnection.Refused)

            listed = true
            rig.pendant.disconnect()
            runCurrent()
            rig.pendant.connect("fake")
            runCurrent()
            advanceTimeBy(100)

            assertTrue(rig.controller.status.value.connection is PendantConnection.Connected)
            assertTrue(rig.sink.frames.isNotEmpty())
        }
}
