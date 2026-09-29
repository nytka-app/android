package io.github.nytka_app.capture

import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureNotificationTest {
    private val connected = PendantConnection.Connected(PendantInfo("Omi"))

    @Test
    fun `reads like the spec`() {
        assertEquals(
            "Recording · 82% · 0 queued",
            CaptureNotification.text(CaptureStatus(running = true, connection = connected, battery = 82), QueueUsage()),
        )
        assertEquals(
            "Muted · 82% · 3 queued",
            CaptureNotification.text(
                CaptureStatus(running = true, connection = connected, muted = true, battery = 82),
                QueueUsage(chunks = 3),
            ),
        )
        assertEquals(
            "Waiting for the pendant · 0 queued",
            CaptureNotification.text(CaptureStatus(running = true), QueueUsage()),
        )
    }

    @Test
    fun `appends the sync progress while a sync runs`() {
        val status = CaptureStatus(running = true, connection = connected, battery = 82)
        val syncing = StorageSyncStatus(state = SyncState.Syncing, runDone = 42, runTotal = 100)

        assertEquals(
            "Recording · 82% · 0 queued · syncing 42%",
            CaptureNotification.text(status, QueueUsage(), syncing),
        )
        assertEquals(
            "Recording · 82% · 0 queued · syncing 42%",
            CaptureNotification.text(
                status,
                QueueUsage(),
                syncing.copy(state = SyncState.WaitingForUploads(0.6)),
            ),
        )
        assertEquals(
            "Recording · 82% · 0 queued",
            CaptureNotification.text(status, QueueUsage(), StorageSyncStatus(state = SyncState.Idle)),
        )
        assertEquals(
            "Recording · 82% · 0 queued",
            CaptureNotification.text(status, QueueUsage(), syncing.copy(state = SyncState.Paused(PauseReason.Stopped))),
        )
    }
}
