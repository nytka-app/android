package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a failed batch alone says nothing, since the server never clears its error`() {
        assertNull(notice(ServerStatus(0, null, "The transcription endpoint answered 503.")))
    }

    @Test
    fun `a failed batch does not hide a backlog`() {
        assertEquals(
            "The server is behind: 40 chunks have waited since 08:00.",
            notice(ServerStatus(40, "2026-09-29T08:00:00Z", "The transcription endpoint answered 503.")),
        )
    }

    @Test
    fun `a time the app cannot read says nothing`() {
        assertNull(notice(ServerStatus(3, "yesterday")))
    }
}
