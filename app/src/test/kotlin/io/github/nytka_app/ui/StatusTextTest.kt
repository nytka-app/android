package io.github.nytka_app.ui

import io.github.nytka_app.core.upload.UploadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class StatusTextTest {
    private val noon = Instant.parse("2026-09-29T12:03:00Z").toEpochMilli()

    @Test
    fun `server line says the most useful thing`() {
        assertEquals("Waiting for the first upload", serverLine(UploadState(), ZoneOffset.UTC))
        assertEquals("Last upload 12:03", serverLine(UploadState(lastUploadAtMs = noon), ZoneOffset.UTC))
        assertEquals(
            "Unreachable since 12:03",
            serverLine(UploadState(lastUploadAtMs = noon - 60_000, unreachableSinceMs = noon), ZoneOffset.UTC),
        )
        assertEquals(
            "Paused: The server refused the token.",
            serverLine(
                UploadState(paused = "The server refused the token.", unreachableSinceMs = noon),
                ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun `uploads are unreachable while they fail on the network, not while the server refuses them`() {
        val refused = UploadState(paused = "The server refused the token.", unreachableSinceMs = noon)

        assertFalse(uploadsUnreachable(UploadState()))
        assertFalse(uploadsUnreachable(UploadState(lastUploadAtMs = noon)))
        assertTrue(uploadsUnreachable(UploadState(lastUploadAtMs = noon - 60_000, unreachableSinceMs = noon)))
        assertFalse(uploadsUnreachable(refused))
    }
}
