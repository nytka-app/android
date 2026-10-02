package io.github.nytka_app.ui.device

import io.github.nytka_app.capture.PauseReason
import io.github.nytka_app.capture.StorageSyncStatus
import io.github.nytka_app.capture.SyncState
import java.util.Locale
import kotlin.math.abs

/** What the Pendant storage card offers besides its text. */
enum class StorageAction { None, SyncNow, Stop, AnswerBacklog }

/** The Pendant storage card, as text. A null card is a card that is hidden. */
data class StorageCard(
    val state: String,
    /** 0 to 1 while a sync runs and knows its size. */
    val progress: Float? = null,
    val details: List<String> = emptyList(),
    val action: StorageAction = StorageAction.None,
)

private const val PACKET_BYTES = 444L
private const val BYTES_PER_KB = 1_000.0
private const val PERCENT = 100

/** True when the sync UI has nothing to say: the server cannot keep live speech ahead of a backlog. */
fun StorageSyncStatus.serverUnsupported(): Boolean = state is SyncState.ServerOutdated

/** The first-sync backlog question, or null when nothing waits for an answer. */
fun StorageSyncStatus.backlogPackets(): Long? = (state as? SyncState.AwaitingBacklog)?.packets

/**
 * The card for [status]. Null (hidden) when no pendant is paired or the server is too old for offline sync; a
 * pendant that cannot store keeps the card, which says why and offers nothing.
 */
fun storageCard(
    status: StorageSyncStatus,
    paired: Boolean,
    connected: Boolean,
): StorageCard? {
    if (!paired || status.serverUnsupported()) return null
    val state = status.state
    val running = state == SyncState.Syncing || state is SyncState.WaitingForUploads
    val progress =
        if (running && status.runTotal > 0) (status.runDone.toFloat() / status.runTotal).coerceIn(0f, 1f) else null
    val details = if (state is SyncState.Unsupported) emptyList() else detailLines(status)
    return StorageCard(stateLine(status), progress, details, action(state, running, connected))
}

private fun action(
    state: SyncState,
    running: Boolean,
    connected: Boolean,
): StorageAction =
    when {
        state is SyncState.Unsupported -> StorageAction.None
        state is SyncState.AwaitingBacklog -> if (connected) StorageAction.AnswerBacklog else StorageAction.None
        running || state == SyncState.Checking -> StorageAction.Stop
        connected -> StorageAction.SyncNow
        else -> StorageAction.None
    }

private fun detailLines(status: StorageSyncStatus): List<String> =
    buildList {
        if (status.syncedPackets > 0) add("Synced: ${packetsToDuration(status.syncedPackets)}")
        if (status.lostPackets > 0) add("Lost: ${packetsToDuration(status.lostPackets)}")
        status.skewCorrectedS?.let { add("Pendant clock was off by ${secondsText(abs(it))}, times corrected") }
    }

private fun stateLine(status: StorageSyncStatus): String {
    val state = status.state
    return when (state) {
        SyncState.Idle ->
            if (status.storedPackets <= 0) {
                "Nothing stored"
            } else {
                "About ${packetsToDuration(status.storedPackets)} stored. It syncs when connected."
            }

        SyncState.Checking -> "Checking the pendant…"
        SyncState.Syncing -> syncingLine(status)
        is SyncState.WaitingForUploads ->
            "Waiting for uploads: the queue is ${(state.queueFraction * PERCENT).toInt()}% full."

        is SyncState.AwaitingBacklog -> "About ${packetsToDuration(state.packets)} stored. Waiting for your answer."
        is SyncState.Paused -> pausedLine(state.reason)
        is SyncState.Retrying ->
            "The pendant did not answer (status ${state.status}). Trying again in ${delayText(state.retryInMs)}."

        is SyncState.ServerUnavailable -> "The server did not answer. Trying again in ${delayText(state.retryInMs)}."
        // The card is hidden for this state; the line only keeps the `when` total.
        is SyncState.ServerOutdated -> "The server is too old for offline sync."
        is SyncState.Unsupported -> state.reason
    }
}

private fun pausedLine(reason: PauseReason): String =
    when (reason) {
        PauseReason.LinkDropped -> "Paused: the link dropped. It resumes when the pendant reconnects."
        PauseReason.LiveLoss -> "Paused: live audio is losing packets. It resumes shortly."
        PauseReason.Stopped -> "Stopped. Sync now continues where it left off."
    }

private fun syncingLine(status: StorageSyncStatus): String {
    val parts = mutableListOf<String>()
    val total = status.runTotal
    if (total > 0) parts += "${(status.runDone * PERCENT / total).coerceIn(0, PERCENT.toLong())}%"
    val rate = status.kbPerSecond
    if (rate != null && rate > 0) {
        parts += "${String.format(Locale.ROOT, "%.0f", rate)} KB/s"
        if (total > status.runDone) {
            val seconds = ((total - status.runDone) * PACKET_BYTES / (rate * BYTES_PER_KB)).toLong()
            parts += "about ${secondsText(seconds.coerceAtLeast(1))} left"
        }
    }
    return if (parts.isEmpty()) "Syncing" else "Syncing " + parts.joinToString(", ")
}
