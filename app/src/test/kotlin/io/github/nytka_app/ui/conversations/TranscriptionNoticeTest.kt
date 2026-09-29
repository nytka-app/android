package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TranscriptionNoticeTest {
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    private fun notice(
        status: ServerStatus,
        zone: ZoneOffset = ZoneOffset.UTC,
    ) = transcriptionNotice(status, now, zone)

    @Test
    fun `a server with nothing wrong says nothing`() {
        assertNull(notice(ServerStatus(pendingChunks = 0)))
    }

    @Test
    fun `a failed batch shows the error the server reports`() {
        assertEquals(
            "Some speech could not be transcribed. The transcription endpoint answered 401.",
            notice(ServerStatus(0, null, "The transcription endpoint answered 401.")),
        )
    }

    @Test
    fun `a failed batch without an error text still says so`() {
        assertEquals("Some speech could not be transcribed.", notice(ServerStatus(0, null, "  ")))
    }

    @Test
    fun `a long error is cut to a line`() {
        val text = notice(ServerStatus(0, null, "x".repeat(300)))!!

        assertTrue(text.endsWith("…"))
        assertEquals("Some speech could not be transcribed. ".length + 120, text.length)
    }

    @Test
    fun `audio held for a quarter of an hour means the server is behind`() {
        assertEquals(
            "The server is behind: 12 chunks have waited since 11:45.",
            notice(ServerStatus(12, "2026-09-29T11:45:00Z")),
        )
        assertEquals(
            "The server is behind: 1 chunk has waited since 09:00.",
            notice(ServerStatus(1, "2026-09-29T09:00:00Z")),
        )
    }

    @Test
    fun `a shorter wait is normal, since the server waits for a missing chunk`() {
        assertNull(notice(ServerStatus(12, "2026-09-29T11:45:01Z")))
        assertNull(notice(ServerStatus(1, "2026-09-29T11:59:00Z")))
    }

    @Test
    fun `the time follows the phone's zone`() {
        assertEquals(
            "The server is behind: 3 chunks have waited since 14:00.",
            notice(ServerStatus(3, "2026-09-29T11:00:00.123456Z"), ZoneOffset.ofHours(3)),
        )
    }

    @Test
    fun `a failed batch comes before a backlog`() {
        assertEquals(
            "Some speech could not be transcribed. The transcription endpoint answered 503.",
            notice(ServerStatus(40, "2026-09-29T08:00:00Z", "The transcription endpoint answered 503.")),
        )
    }

    @Test
    fun `a time the app cannot read says nothing`() {
        assertNull(notice(ServerStatus(3, "yesterday")))
    }
}
