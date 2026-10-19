package io.github.nytka_app.capture

import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureNotificationTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    private val connected = PendantConnection.Connected(PendantInfo("Omi"))

    @Test
    fun `the title is the state word and leads with it`() {
        assertEquals(
            CaptureState.Recording,
            CaptureNotification.state(CaptureStatus(running = true, connection = connected)),
        )
        assertEquals(
            CaptureState.Muted,
            CaptureNotification.state(CaptureStatus(running = true, connection = connected, muted = true)),
        )
        assertEquals(CaptureState.Waiting, CaptureNotification.state(CaptureStatus(running = true)))
        assertEquals(
            CaptureState.Waiting,
            CaptureNotification.state(
                CaptureStatus(running = true, connection = PendantConnection.Refused("old firmware")),
            ),
        )
    }

    @Test
    fun `names a refused pendant first`() {
        assertEquals(
            "Pendant not supported · 0 queued",
            CaptureNotification.text(
                resources,
                CaptureStatus(running = true, connection = PendantConnection.Refused("old firmware")),
                QueueUsage(),
            ),
        )
    }

    @Test
    fun `reads like the spec`() {
        assertEquals(
            "82% · 0 queued",
            CaptureNotification.text(
                resources,
                CaptureStatus(running = true, connection = connected, battery = 82),
                QueueUsage(),
            ),
        )
        assertEquals(
            "82% · 3 queued",
            CaptureNotification.text(
                resources,
                CaptureStatus(running = true, connection = connected, muted = true, battery = 82),
                QueueUsage(chunks = 3),
            ),
        )
        assertEquals(
            "0 queued",
            CaptureNotification.text(resources, CaptureStatus(running = true), QueueUsage()),
        )
    }

    @Test
    fun `appends the sync progress while a sync runs`() {
        val status = CaptureStatus(running = true, connection = connected, battery = 82)
        val syncing = StorageSyncStatus(state = SyncState.Syncing, runDone = 42, runTotal = 100)

        assertEquals(
            "82% · 0 queued · syncing 42%",
            CaptureNotification.text(resources, status, QueueUsage(), syncing),
        )
        assertEquals(
            "82% · 0 queued · syncing 42%",
            CaptureNotification.text(
                resources,
                status,
                QueueUsage(),
                syncing.copy(state = SyncState.WaitingForUploads(0.6)),
            ),
        )
        assertEquals(
            "82% · 0 queued",
            CaptureNotification.text(resources, status, QueueUsage(), StorageSyncStatus(state = SyncState.Idle)),
        )
        assertEquals(
            "82% · 0 queued",
            CaptureNotification.text(
                resources,
                status,
                QueueUsage(),
                syncing.copy(state = SyncState.Paused(PauseReason.Stopped)),
            ),
        )
    }
}
