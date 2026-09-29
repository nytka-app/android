package io.github.nytka_app.ui.device

import io.github.nytka_app.capture.PauseReason
import io.github.nytka_app.capture.StorageSyncStatus
import io.github.nytka_app.capture.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageCardModelTest {
    private fun card(
        status: StorageSyncStatus,
        connected: Boolean = true,
    ) = storageCard(status, paired = true, connected = connected)!!

    @Test
    fun `packets become minutes at 80 ms each`() {
        assertEquals("under a minute", packetsToDuration(100))
        assertEquals("12 min", packetsToDuration(9_000))
        assertEquals("2 h", packetsToDuration(90_000))
        assertEquals("2 h 5 min", packetsToDuration(93_750))
    }

    @Test
    fun `idle says what is stored`() {
        assertEquals("Nothing stored", card(StorageSyncStatus()).state)
        assertEquals(
            "About 12 min stored. It syncs when connected.",
            card(StorageSyncStatus(storedPackets = 9_000)).state,
        )
    }

    @Test
    fun `syncing shows percent, rate and time left`() {
        // 1,000 packets left is 444 KB; at 3.7 KB/s that is 120 s.
        val syncing = StorageSyncStatus(SyncState.Syncing, runDone = 1_000, runTotal = 2_000, kbPerSecond = 3.7)

        val shown = card(syncing)

        assertEquals("Syncing 50%, 4 KB/s, about 2 min left", shown.state)
        assertEquals(0.5f, shown.progress)
        assertEquals(StorageAction.Stop, shown.action)
    }

    @Test
    fun `syncing without a rate yet shows the percent only`() {
        assertEquals("Syncing 25%", card(StorageSyncStatus(SyncState.Syncing, runDone = 500, runTotal = 2_000)).state)
        assertEquals("Syncing", card(StorageSyncStatus(SyncState.Syncing)).state)
    }

    @Test
    fun `stuck states say why and when`() {
        assertEquals(
            "Waiting for uploads: the queue is 61% full.",
            card(StorageSyncStatus(SyncState.WaitingForUploads(0.61))).state,
        )
        assertEquals(
            "Paused: the link dropped. It resumes when the pendant reconnects.",
            card(StorageSyncStatus(SyncState.Paused(PauseReason.LinkDropped)), connected = false).state,
        )
        assertEquals(
            "The pendant did not answer (status 9). Trying again in 2 min.",
            card(StorageSyncStatus(SyncState.Retrying(9, 120_000))).state,
        )
    }

    @Test
    fun `sync now shows when idle and connected, and not otherwise`() {
        assertEquals(StorageAction.SyncNow, card(StorageSyncStatus()).action)
        assertEquals(StorageAction.None, card(StorageSyncStatus(), connected = false).action)
        assertEquals(StorageAction.SyncNow, card(StorageSyncStatus(SyncState.Paused(PauseReason.Stopped))).action)
    }

    @Test
    fun `details show synced, lost and a clock correction only when there is something to say`() {
        assertEquals(emptyList<String>(), card(StorageSyncStatus()).details)

        val details =
            card(
                StorageSyncStatus(syncedPackets = 30_750, lostPackets = 9_000, skewCorrectedS = -360),
            ).details

        assertEquals(
            listOf("Synced: 41 min", "Lost: 12 min", "Pendant clock was off by 6 min, times corrected"),
            details,
        )
    }

    @Test
    fun `an unsupported pendant gets its reason and nothing else`() {
        val shown =
            card(
                StorageSyncStatus(
                    SyncState.Unsupported("Offline sync needs firmware 3.0.20 or later."),
                    syncedPackets = 5,
                ),
            )

        assertEquals("Offline sync needs firmware 3.0.20 or later.", shown.state)
        assertEquals(StorageAction.None, shown.action)
        assertEquals(emptyList<String>(), shown.details)
    }

    @Test
    fun `no card without a pendant or on an outdated server`() {
        assertNull(storageCard(StorageSyncStatus(), paired = false, connected = true))
        assertNull(
            storageCard(StorageSyncStatus(SyncState.ServerOutdated(1_000)), paired = true, connected = true),
        )
    }
}
