package io.github.nytka_app.capture

import io.github.nytka_app.pendant.RingInfo

// STUB of track 3's types (StorageSyncController.kt on feat/v0.3-sync-flow), so the screens compile before it merges.
// Delete this file when track 3 lands: the declarations there are the same.

/** Why a sync is not running although the pendant is connected. */
enum class PauseReason { LinkDropped, LiveLoss, Stopped }

/** What the sync is doing, for the Device tab and the notification. */
sealed interface SyncState {
    /** Nothing to do, or done. */
    data object Idle : SyncState

    /** Asking the server and the pendant before any read. */
    data object Checking : SyncState

    data object Syncing : SyncState

    /** The queue is over half its cap; the sync resumes below 40%. */
    data class WaitingForUploads(
        val queueFraction: Double,
    ) : SyncState

    /** The first sync found [packets] unread: [StorageSyncController.importBacklog] or `discardBacklog`. */
    data class AwaitingBacklog(
        val packets: Long,
    ) : SyncState

    data class Paused(
        val reason: PauseReason,
    ) : SyncState

    /** The pendant did not answer well ([status] is a `RingStatus` value); the next try is in [retryInMs]. */
    data class Retrying(
        val status: Int,
        val retryInMs: Long,
    ) : SyncState

    /** `/api/v1/info` did not answer. */
    data class ServerUnavailable(
        val retryInMs: Long,
    ) : SyncState

    /** The server is older than the one that keeps live speech ahead of a backlog. */
    data class ServerOutdated(
        val version: String,
        val retryInMs: Long,
    ) : SyncState

    /** [reason] is shown to the user as is. */
    data class Unsupported(
        val reason: String,
    ) : SyncState
}

/**
 * What the screens see of the sync. The first seven fields are the v0.3 contract; the rest feed developer mode and
 * the diagnostics samples.
 */
data class StorageSyncStatus(
    val state: SyncState = SyncState.Idle,
    /** Packets the pendant holds that this phone has not read yet, as of the last INFO and commit. */
    val storedPackets: Long = 0,
    val runDone: Long = 0,
    val runTotal: Long = 0,
    val kbPerSecond: Double? = null,
    /** Packets the pendant freed or overwrote before this phone read them. */
    val lostPackets: Long = 0,
    val skewS: Long? = null,
    val ring: RingInfo? = null,
    val lastDoneStatus: Int? = null,
    /** Share of the live stream's notifications lost during the last window, when there were enough of them. */
    val liveLoss: Double? = null,
    val mutedFrames: Long = 0,
    val badStampRecords: Long = 0,
    val segments: Long = 0,
    val syncedPackets: Long = 0,
    /** The last skew correction applied to stamps (seconds), for "Pendant clock was off by ...". */
    val skewCorrectedS: Long? = null,
)
