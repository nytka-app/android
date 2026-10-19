package io.github.nytka_app.capture

import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureStateTest {
    private val connected = PendantConnection.Connected(PendantInfo("Omi"))

    @Test
    fun `capture off reads off, whatever else holds`() {
        assertEquals(CaptureState.Off, CaptureState.of(CaptureStatus()))
        assertEquals(CaptureState.Off, CaptureState.of(CaptureStatus(connection = connected), muted = true))
    }

    @Test
    fun `the muted setting wins over a connected pendant`() {
        assertEquals(
            CaptureState.Muted,
            CaptureState.of(CaptureStatus(running = true, connection = connected), muted = true),
        )
        assertEquals(CaptureState.Muted, CaptureState.of(CaptureStatus(running = true), muted = true))
    }

    @Test
    fun `recording needs a connected pendant`() {
        assertEquals(CaptureState.Recording, CaptureState.of(CaptureStatus(running = true, connection = connected)))
        assertEquals(CaptureState.Waiting, CaptureState.of(CaptureStatus(running = true)))
        assertEquals(
            CaptureState.Waiting,
            CaptureState.of(CaptureStatus(running = true, connection = PendantConnection.Connecting)),
        )
    }

    @Test
    fun `each state has its own words and glyph`() {
        assertEquals(
            4,
            CaptureState.entries
                .map { it.wordRes }
                .distinct()
                .size,
        )
        assertEquals(
            4,
            CaptureState.entries
                .map { it.iconRes }
                .distinct()
                .size,
        )
    }
}
