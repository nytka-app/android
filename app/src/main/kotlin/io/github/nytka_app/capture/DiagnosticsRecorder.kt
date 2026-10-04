package io.github.nytka_app.capture

import io.github.nytka_app.core.diagnostics.DiagnosticSample
import io.github.nytka_app.core.diagnostics.DiagnosticsSink
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.diagnostics.Uuid7
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.upload.UploadState
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Takes a [DiagnosticSample] every 10 s while capture runs, and one more when it stops, and forgets
 * samples older than seven days once an hour. The samples hold counters and short status words only.
 */
class DiagnosticsRecorder(
    private val status: StateFlow<CaptureStatus>,
    private val usage: StateFlow<QueueUsage>,
    private val upload: StateFlow<UploadState>,
    private val sink: DiagnosticsSink,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val device: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: (Long) -> UUID = { Uuid7.next(it) },
    private val everyMs: Long = SAMPLE_EVERY_MS,
    private val log: EventLog = EventLog.Logcat,
    private val sync: StateFlow<StorageSyncStatus>? = null,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job =
            scope.launch {
                var ticks = 0
                prune()
                while (true) {
                    record()
                    delay(everyMs)
                    if (++ticks % PRUNE_EVERY_TICKS == 0) prune()
                }
            }
    }

    /** Ends the sampling and takes a last sample of the state as it is. */
    suspend fun stop() {
        val running = job ?: return
        running.cancelAndJoin()
        job = null
        record()
    }

    private suspend fun record() = stored { sink.add(sample()) }

    private suspend fun prune() = stored { sink.prune() }

    // Nothing here may crash the process or the service's shutdown: a missed sample is only a gap in a chart.
    @Suppress("TooGenericExceptionCaught")
    private inline fun stored(write: () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(TAG, "The diagnostics log refused a write: ${e.javaClass.simpleName}")
        }
    }

    private fun sample(): DiagnosticSample {
        val capture = status.value
        val queue = usage.value
        val uploader = upload.value
        val at = now()
        val storage = sync?.value
        return DiagnosticSample(
            id = newId(at).toString(),
            at = Instant.ofEpochMilli(at).toString(),
            session = capture.session,
            connection = connectionWord(capture),
            battery = capture.battery,
            notifications = capture.stats.notifications,
            lostNotifications = capture.stats.lostNotifications,
            droppedFrames = capture.stats.droppedFrames,
            frames = capture.stats.frames,
            framesQueued = capture.framesQueued,
            queueBytes = queue.bytes,
            queueChunks = queue.chunks,
            queueFrames = queue.frames,
            uploadFailures = uploader.failures,
            uploadPaused = uploader.paused?.let(::pauseWord),
            lastUploadAt = uploader.lastUploadAtMs?.let { Instant.ofEpochMilli(it).toString() },
            lastResult = uploader.lastResult?.let(::resultWord),
            appVersion = appVersion,
            device = device,
            syncState = storage?.let { syncWord(it.state) },
            ringReadSeq = storage?.ring?.readSeq,
            ringWriteSeq = storage?.ring?.writeSeq,
            ringCapacity = storage?.ring?.capacityPackets,
            ringDropped = storage?.ring?.droppedPackets,
            lastDoneStatus = storage?.lastDoneStatus,
            syncedPackets = storage?.syncedPackets ?: 0,
            lostPackets = storage?.lostPackets ?: 0,
            syncKbPerSecond = storage?.kbPerSecond,
            syncLiveLoss = storage?.liveLoss,
            mutedFrames = storage?.mutedFrames ?: 0,
            badStampRecords = storage?.badStampRecords ?: 0,
            clockSkewS = storage?.skewS,
            segments = storage?.segments ?: 0,
            parkedChunks = queue.parkedChunks,
            rssi = capture.stats.rssi,
            lastDisconnectStatus = capture.stats.lastDisconnectStatus,
        )
    }

    companion object {
        const val SAMPLE_EVERY_MS = 10_000L
        private const val PRUNE_EVERY_TICKS = 360
        private const val TAG = "DiagnosticsRecorder"
        private val statusCode = Regex("""^(\d{3})\b|^The server answered (\d{3})\.""")

        /** The sync state as a short word: no reasons, versions or addresses leave the phone. */
        fun syncWord(state: SyncState): String =
            when (state) {
                SyncState.Idle -> "Idle"
                SyncState.Checking -> "Checking"
                SyncState.Syncing -> "Syncing"
                is SyncState.WaitingForUploads -> "WaitingForUploads"
                is SyncState.AwaitingBacklog -> "AwaitingBacklog"
                is SyncState.Paused -> "Paused${state.reason.name}"
                is SyncState.Retrying -> "Retrying"
                is SyncState.ServerUnavailable -> "ServerUnavailable"
                is SyncState.ServerOutdated -> "ServerOutdated"
                is SyncState.Unsupported -> "Unsupported"
            }

        fun connectionWord(capture: CaptureStatus): String =
            when (capture.connection) {
                is PendantConnection.Connected -> if (capture.muted) "muted" else "connected"
                PendantConnection.Connecting -> "connecting"
                PendantConnection.Disconnected -> "disconnected"
                is PendantConnection.Refused -> "refused"
            }

        /**
         * The uploader's messages can carry a host name (a network error) or a chunk number: only the HTTP
         * status, "Retry" or the exception's class name leave it.
         */
        fun resultWord(result: String): String =
            statusCode
                .find(result)
                ?.groupValues
                ?.drop(1)
                ?.first(String::isNotEmpty)
                ?: result.takeIf { it.startsWith("Upload failed: ") }
                ?: pauseWord(result).takeIf { it != "Paused" }
                ?: "Retry"

        fun pauseWord(reason: String): String =
            when {
                reason.startsWith("The server refused") -> "Unauthorized"
                reason.startsWith("No token") || reason.startsWith("Enter ") || reason.startsWith("Plain http") ->
                    "NotConfigured"

                else -> "Paused"
            }
    }
}
