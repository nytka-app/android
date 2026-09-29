package io.github.nytka_app.capture

import android.database.SQLException
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.ring.CaptureTimes
import io.github.nytka_app.core.ring.ClockPair
import io.github.nytka_app.core.ring.MuteFilter
import io.github.nytka_app.core.ring.RingCaptureTimes
import io.github.nytka_app.core.ring.RingPosition
import io.github.nytka_app.core.ring.StoredRecord
import io.github.nytka_app.core.ring.TimeState
import io.github.nytka_app.pendant.LinkStats
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.RingEvent
import io.github.nytka_app.pendant.RingInfo
import io.github.nytka_app.pendant.RingProtocol
import io.github.nytka_app.pendant.RingRecord
import io.github.nytka_app.pendant.RingStatus
import io.github.nytka_app.pendant.StorageSupport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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

/** The guesses of the v0.3 spec (open decision 4) in one place, so tests and tuning need no edits to the flow. */
data class SyncTuning(
    val windowPackets: Int = 2_000,
    val commitRecords: Int = 20,
    val commitMs: Long = 100,
    val holdAboveQueue: Double = 0.5,
    val resumeBelowQueue: Double = 0.4,
    val backlogPackets: Long = 90_000,
    val notReadyRetryMs: Long = 2_000,
    val notReadyGiveUpMs: Long = 20_000,
    val retryDelaysMs: List<Long> = listOf(30_000, 120_000, 600_000),
    val liveLossFraction: Double = 0.05,
    val liveLossMinNotifications: Long = 50,
    val liveLossPauseMs: Long = 30_000,
    val liveLossPauses: Int = 3,
    val advanceEveryMs: Long = 10_000,
)

/**
 * Copies the pendant's stored audio into the queue while capture runs (spec "Sync flow"). Per connection: check
 * the server and the pendant, read the ring in windows from `committedNext`, turn records into timed frames,
 * drop what a mute covers, commit frames and position together, and wait when the queue is filling.
 *
 * `ADVANCE` is a separate loop that runs while no window transfers and only ever moves to `ackedThrough`: what the
 * queue has neither uploaded nor parked keeps its ring range. Live capture is [CaptureController]'s and is never
 * blocked here: the sync pauses for its backpressure and for live loss, and yields between batches.
 */
@Suppress("TooManyFunctions", "LargeClass")
class StorageSyncController(
    private val pendant: Pendant,
    private val sink: FrameSink,
    private val usage: StateFlow<QueueUsage>,
    private val info: InfoClient,
    private val scope: CoroutineScope,
    private val address: String,
    private val times: CaptureTimes = RingCaptureTimes(),
    private val now: () -> Long = System::currentTimeMillis,
    private val tuning: SyncTuning = SyncTuning(),
    private val minServerVersion: String = MIN_SERVER_VERSION,
    private val log: EventLog = EventLog.Logcat,
) {
    private val storage = pendant.storage
    private val mutableStatus = MutableStateFlow(StorageSyncStatus())
    val status: StateFlow<StorageSyncStatus> = mutableStatus.asStateFlow()

    private val transferring = MutableStateFlow(false)
    private var watcher: Job? = null
    private var syncJob: Job? = null
    private var advanceJob: Job? = null
    private var backlogAnswer: CompletableDeferred<Boolean>? = null

    /** Stop was pressed: no sync until the next connection or [syncNow]. */
    @Volatile private var held = false

    // The sync of the current connection.
    private var position: RingPosition? = null
    private var timeState: TimeState? = null
    private var clock: ClockPair? = null
    private var connectionClock: ClockPair? = null
    private var lossPauses = 0
    private var progressed = false

    /** The last `ADVANCE` the pendant took (or was already past); starts at what the position says. */
    private var lastAdvanced = 0L
    private var lastAdvanceAtMs = Long.MIN_VALUE / 2

    fun start() {
        if (watcher != null) return
        watcher =
            scope.launch {
                combine(pendant.connection, storage.support) { connection, support ->
                    (connection is PendantConnection.Connected) to support
                }.distinctUntilChanged().collect { (connected, support) -> onLink(connected, support) }
            }
    }

    fun stop() {
        watcher?.cancel()
        watcher = null
        syncJob?.cancel()
        advanceJob?.cancel()
        backlogAnswer = null
        mutableStatus.value = StorageSyncStatus()
    }

    /** "Sync now": starts a sync unless one is running. */
    fun syncNow() {
        if (pendant.connection.value !is PendantConnection.Connected) return
        if (storage.support.value != StorageSupport.Supported) return
        when (status.value.state) {
            SyncState.Checking,
            SyncState.Syncing,
            is SyncState.WaitingForUploads,
            is SyncState.AwaitingBacklog,
            -> return
            else -> Unit
        }
        held = false
        lossPauses = 0
        startSync()
    }

    /** "Stop": holds the sync until the next connection or [syncNow]. What was committed stays. */
    fun stopSync() {
        held = true
        syncJob?.cancel()
        backlogAnswer = null
        transferring.value = false
        setState(SyncState.Paused(PauseReason.Stopped))
    }

    /** The user chose to import a large first backlog (also what happens when nobody answers and the app restarts). */
    fun importBacklog() {
        backlogAnswer?.complete(true)
    }

    /** The user confirmed discarding a large first backlog; the ring is freed by the normal `ADVANCE`. */
    fun discardBacklog() {
        backlogAnswer?.complete(false)
    }

    private fun onLink(
        connected: Boolean,
        support: StorageSupport,
    ) {
        if (!connected) {
            linkDropped()
            return
        }
        when (support) {
            StorageSupport.Unknown -> Unit
            is StorageSupport.Unsupported -> setState(SyncState.Unsupported(support.reason))
            StorageSupport.Supported -> {
                if (advanceJob?.isActive != true) advanceJob = scope.launch { advanceLoop() }
                if (!held && syncJob?.isActive != true) startSync()
            }
        }
    }

    private fun linkDropped() {
        val busy = syncJob?.isActive == true
        syncJob?.cancel()
        advanceJob?.cancel()
        backlogAnswer = null
        transferring.value = false
        held = false
        timeState = null
        lossPauses = 0
        // A sync that already ended on the lost link said so itself; anything else that ended goes back to Idle.
        val shown = status.value.state
        val paused = SyncState.Paused(PauseReason.LinkDropped)
        setState(if (busy || shown == paused) paused else SyncState.Idle)
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch { syncLoop() }
    }

    private fun setState(state: SyncState) = mutableStatus.update { it.copy(state = state) }

    // ---- The sync ----

    private sealed interface Outcome {
        data object Complete : Outcome

        /** The ring was reset under the sync: begin again at once. */
        data object Again : Outcome

        class Stop(
            val state: SyncState,
        ) : Outcome

        class Retry(
            val note: String,
            val state: (Long) -> SyncState,
        ) : Outcome
    }

    private suspend fun syncLoop() {
        var failures = 0
        while (true) {
            progressed = false
            val outcome =
                try {
                    attempt()
                } catch (e: SQLException) {
                    Outcome.Retry("the queue refused a write: ${e.javaClass.simpleName}") {
                        SyncState.Retrying(RingStatus.TIMEOUT, it)
                    }
                }
            if (progressed) failures = 0
            when (outcome) {
                Outcome.Complete -> {
                    setState(SyncState.Idle)
                    return
                }
                Outcome.Again -> Unit
                is Outcome.Stop -> {
                    setState(outcome.state)
                    return
                }
                is Outcome.Retry -> {
                    val wait = tuning.retryDelaysMs[minOf(failures++, tuning.retryDelaysMs.lastIndex)]
                    log.w(TAG, "sync retry in ${wait / MS}s: ${outcome.note}")
                    setState(outcome.state(wait))
                    delay(wait)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun attempt(): Outcome {
        setState(SyncState.Checking)
        serverGate()?.let { return it }
        val ring = readInfo() ?: return infoFailed()
        if (ring.packetBytes != RingProtocol.RECORD_BYTES) {
            return Outcome.Stop(
                SyncState.Unsupported(
                    "This pendant stores ${ring.packetBytes}-byte records; Nytka reads ${RingProtocol.RECORD_BYTES}.",
                ),
            )
        }
        val first = openPosition(ring)
        if (first && ring.writeSeq - ring.readSeq > tuning.backlogPackets) askAboutBacklog(ring)

        val committed = requireNotNull(position).committedNext
        mutableStatus.update { it.copy(runDone = 0, runTotal = ring.writeSeq - committed) }
        log.i(TAG, "sync starts: ${ring.writeSeq - committed} packets, pendant battery ${pendant.battery.value}%")
        setState(SyncState.Syncing)
        while (requireNotNull(position).committedNext < ring.writeSeq) {
            waitForQueue()
            window(ring)?.let { return it }
        }
        mutableStatus.update { it.copy(storedPackets = 0) }
        log.i(TAG, "sync done: pendant battery ${pendant.battery.value}%, lost ${status.value.lostPackets} packets")
        return Outcome.Complete
    }

    /**
     * Loads or creates the sync position for INFO's [ring], settles the clock pair and stores what changed. True
     * for a first sync: no position, or one of another pendant (whose epoch the new one follows).
     */
    private suspend fun openPosition(ring: RingInfo): Boolean {
        val loaded = sink.position()
        val first = loaded == null || loaded.pendant != address
        var current =
            if (first) {
                RingPosition(
                    pendant = address,
                    committedNext = ring.readSeq,
                    epoch = loaded?.let { it.epoch + 1 } ?: 0,
                    advanced = ring.readSeq,
                    lastDropped = ring.droppedPackets,
                )
            } else {
                reconcile(requireNotNull(loaded), ring)
            }
        connectionClock = storage.clockSkew.value?.let { ClockPair(ring.writeSeq, it) }
        // The old pair stays while records below its writeSeq are unread (see the KDoc on RingCaptureTimes).
        val old = current.clock
        clock = if (old != null && current.committedNext < old.writeSeq) old else connectionClock ?: old
        current = current.copy(clock = clock, lastDropped = ring.droppedPackets)
        if (current != loaded) sink.commit(emptyList(), current)
        position = current
        lastAdvanced = maxOf(lastAdvanced, current.advanced, ring.readSeq)
        if (timeState == null) timeState = current.newRun()
        mutableStatus.update {
            it.copy(
                ring = ring,
                storedPackets = ring.writeSeq - current.committedNext,
                skewS = clock?.skewS ?: storage.clockSkew.value,
            )
        }
        return first
    }

    /** No sync against a server that runs a backlog FIFO ahead of live speech (spec "Sync flow" 1). */
    private suspend fun serverGate(): Outcome? {
        val answer = info.info() as? ApiResult.Ok
        if (answer == null) {
            return Outcome.Retry("the server did not answer /info") { SyncState.ServerUnavailable(it) }
        }
        val version = answer.value.serverVersion
        if (serverAtLeast(version, minServerVersion)) return null
        return Outcome.Retry("the server runs $version, sync needs $minServerVersion") {
            SyncState.ServerOutdated(version, it)
        }
    }

    /** INFO, retried every 2 s for 20 s while the pendant says its storage is not ready (status 9). */
    private suspend fun readInfo(): RingInfo? {
        var waited = 0L
        while (true) {
            storage.info()?.let { return it }
            if (storage.lastStatus.value != RingStatus.NOT_READY || waited >= tuning.notReadyGiveUpMs) return null
            delay(tuning.notReadyRetryMs)
            waited += tuning.notReadyRetryMs
        }
    }

    private fun infoFailed(): Outcome {
        val status = storage.lastStatus.value ?: RingStatus.TIMEOUT
        return if (status == RingStatus.LINK_LOST) {
            Outcome.Stop(SyncState.Paused(PauseReason.LinkDropped))
        } else {
            Outcome.Retry("INFO failed with status $status") { SyncState.Retrying(status, it) }
        }
    }

    /** A cleared ring starts a new epoch; audio the pendant no longer holds (freed early, overwritten) is lost. */
    private fun reconcile(
        loaded: RingPosition,
        ring: RingInfo,
    ): RingPosition {
        if (loaded.wasCleared(ring.writeSeq, ring.droppedPackets)) {
            log.w(TAG, "the pendant's ring was cleared: a new epoch starts at ${ring.readSeq}")
            timeState = null
            return loaded.newEpoch(ring.readSeq, ring.droppedPackets)
        }
        if (ring.readSeq > loaded.committedNext) {
            val lost = ring.readSeq - loaded.committedNext
            log.w(TAG, "lost $lost packets (about ${lost * PACKET_MS / MS}s): the pendant freed them before reading")
            mutableStatus.update { it.copy(lostPackets = it.lostPackets + lost) }
            return loaded.copy(committedNext = ring.readSeq)
        }
        return loaded
    }

    /**
     * Ask once. The position is already stored, so a restart before an answer imports (the default); an answer of
     * "discard" moves the position to `writeSeq` and the ordinary `ADVANCE` frees the ring.
     */
    private suspend fun askAboutBacklog(ring: RingInfo) {
        val unread = ring.writeSeq - ring.readSeq
        setState(SyncState.AwaitingBacklog(unread))
        log.i(TAG, "first sync finds $unread packets: asking whether to import")
        val answer = CompletableDeferred<Boolean>()
        backlogAnswer = answer
        val import = answer.await()
        backlogAnswer = null
        if (import) return
        val discarded = requireNotNull(position).copy(committedNext = ring.writeSeq)
        sink.commit(emptyList(), discarded)
        position = discarded
        log.i(TAG, "first sync: $unread packets discarded on request")
    }

    /** Waits while the queue is over half its cap: a chunk dropped there is audio the pendant may have freed. */
    private suspend fun waitForQueue() {
        if (usage.value.fraction <= tuning.holdAboveQueue) return
        setState(SyncState.WaitingForUploads(usage.value.fraction))
        usage.first { it.fraction < tuning.resumeBelowQueue }
        setState(SyncState.Syncing)
    }

    /** One window. Null means carry on with the next; anything else ends the attempt. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun window(ring: RingInfo): Outcome? {
        val from = requireNotNull(position).committedNext
        val mute = MuteFilter(sink.muteChanges())
        val statsBefore = pendant.stats.value
        val startedAt = now()
        val pending = mutableListOf<RingRecord>()
        var flushedAt = startedAt
        var done: RingEvent.Done? = null
        var refilled = false
        var checked = false
        var packets = 0L

        suspend fun flush() {
            if (pending.isEmpty()) return
            val records = pending.map { StoredRecord(it.seq, it.stampS, it.frames) }
            val next = pending.last().seq + 1
            pending.clear()
            val before = checkNotNull(timeState)
            val timed = mute.apply(times.assign(records, before, clock, now()), before)
            val committed =
                requireNotNull(position).after(timed, next).copy(clock = clock, lastDropped = ring.droppedPackets)
            sink.commit(timed.frames, committed)
            position = committed
            timeState = timed.state
            packets += records.size
            progressed = true
            mutableStatus.update {
                it.copy(
                    runDone = it.runDone + records.size,
                    syncedPackets = it.syncedPackets + records.size,
                    storedPackets = ring.writeSeq - next,
                    mutedFrames = it.mutedFrames + timed.mutedFrames,
                    badStampRecords = it.badStampRecords + timed.futureRecords,
                    segments = it.segments + timed.segments,
                    skewCorrectedS = timed.skewCorrectedS ?: it.skewCorrectedS,
                )
            }
        }

        transferring.value = true
        try {
            storage
                .read(from, tuning.windowPackets)
                .takeWhile { event ->
                    when (event) {
                        is RingEvent.Begin -> Unit
                        is RingEvent.Records -> {
                            if (!checked && event.records.isNotEmpty()) {
                                checked = true
                                // A clear and a refill past committedNext shows only in the stamps.
                                if (requireNotNull(position).wasRefilled(event.records.first().stampS)) {
                                    refilled = true
                                    return@takeWhile false
                                }
                            }
                            pending += event.records
                            if (pending.size >= tuning.commitRecords || now() - flushedAt >= tuning.commitMs) {
                                flush()
                                flushedAt = now()
                            }
                            yield() // live capture shares the dispatcher
                        }
                        is RingEvent.Done -> done = event
                    }
                    true
                }.collect()
            flush()
        } finally {
            transferring.value = false
        }
        // The frames become chunks now, so the uploader can take them while the next window reads.
        sink.seal()

        val elapsedMs = now() - startedAt
        val kbPerSecond =
            if (elapsedMs > 0) packets * RingProtocol.RECORD_BYTES / KIB / (elapsedMs / MS.toDouble()) else null
        val status = done?.status
        mutableStatus.update { it.copy(kbPerSecond = kbPerSecond ?: it.kbPerSecond, lastDoneStatus = status) }
        log.i(
            TAG,
            "window from $from: $packets packets, ${kbPerSecond?.let {
                "%.0f".format(
                    it,
                )
            } ?: "?"} KB/s, status $status",
        )

        if (refilled) return restartEpoch(ring)
        return when (status) {
            RingStatus.OK -> if (packets == 0L) noProgress() else liveLoss(statsBefore)
            RingStatus.LINK_LOST -> Outcome.Stop(SyncState.Paused(PauseReason.LinkDropped))
            else ->
                Outcome.Retry("READ ended with status $status") { SyncState.Retrying(status ?: RingStatus.TIMEOUT, it) }
        }
    }

    private fun noProgress(): Outcome =
        Outcome.Retry("READ answered ok but delivered nothing") { SyncState.Retrying(RingStatus.OK, it) }

    /** The first record read is older than the last committed one: the ring was cleared and refilled. */
    private suspend fun restartEpoch(ring: RingInfo): Outcome {
        log.w(TAG, "the first record read is older than the last committed one: a new epoch starts at ${ring.readSeq}")
        val fresh = requireNotNull(position).newEpoch(ring.readSeq, ring.droppedPackets).copy(clock = connectionClock)
        sink.commit(emptyList(), fresh)
        position = fresh
        timeState = null
        return Outcome.Again
    }

    /** Live loss over 5% in a window pauses the sync for 30 s; three pauses in a row end it for this connection. */
    private suspend fun liveLoss(before: LinkStats): Outcome? {
        val after = pendant.stats.value
        val lost = after.lostNotifications - before.lostNotifications
        val total = lost + after.notifications - before.notifications
        if (total < tuning.liveLossMinNotifications) return null
        val loss = lost.toDouble() / total
        mutableStatus.update { it.copy(liveLoss = loss) }
        if (loss <= tuning.liveLossFraction) {
            lossPauses = 0
            return null
        }
        lossPauses++
        log.w(TAG, "live stream lost ${"%.1f".format(loss * PERCENT)}% during a window: pause $lossPauses")
        if (lossPauses >= tuning.liveLossPauses) return Outcome.Stop(SyncState.Paused(PauseReason.LiveLoss))
        setState(SyncState.Paused(PauseReason.LiveLoss))
        delay(tuning.liveLossPauseMs)
        setState(SyncState.Syncing)
        return null
    }

    // ---- ADVANCE ----

    /**
     * Frees the ring up to `ackedThrough` when no transfer runs, at most every 10 s. The target is capped by the
     * stored position too, so nothing the queue still holds can be freed.
     */
    private suspend fun advanceLoop() {
        while (advanceOnce()) Unit
    }

    /** One ADVANCE when there is something to free; false when the loop should end (no link, or nothing will come). */
    private suspend fun advanceOnce(): Boolean {
        if (sink.ackedThrough.firstOrNull { it > lastAdvanced } == null) return false
        transferring.first { !it }
        val wait = lastAdvanceAtMs + tuning.advanceEveryMs - now()
        if (wait > 0) delay(wait)
        transferring.first { !it }
        val committed = sink.position()?.committedNext ?: return true
        val acked = sink.ackedThrough.firstOrNull() ?: return false
        val target = minOf(acked, committed)
        if (target <= lastAdvanced) return true
        val status = storage.advance(target)
        lastAdvanceAtMs = now()
        when (status) {
            // Status 10: the firmware is already past that point (3.0.21 advances on its own).
            RingStatus.OK, RingStatus.OUT_OF_RANGE -> {
                lastAdvanced = target
                try {
                    sink.markAdvanced(target)
                } catch (e: SQLException) {
                    log.w(TAG, "the position refused ADVANCE($target): ${e.javaClass.simpleName}")
                }
                log.i(TAG, "advanced the ring to $target (status $status)")
            }
            RingStatus.LINK_LOST, RingStatus.UNAVAILABLE -> return false
            else -> log.w(TAG, "ADVANCE($target) answered $status, trying again later")
        }
        return true
    }

    companion object {
        /** The server that ships migration 0005 (job priority); release-please bumps a 0.x minor for `feat:`. */
        const val MIN_SERVER_VERSION = "0.4.0"
        private const val TAG = "StorageSync"
        private const val MS = 1_000L
        private const val PACKET_MS = 80L
        private const val KIB = 1024.0
        private const val PERCENT = 100

        private val versionPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")

        /** True when [version] is [minimum] or later; an unparsable version is not. A pre-release suffix is ignored. */
        fun serverAtLeast(
            version: String,
            minimum: String,
        ): Boolean {
            val have = parts(version) ?: return false
            val need = parts(minimum) ?: return false
            for (i in have.indices) if (have[i] != need[i]) return have[i] > need[i]
            return true
        }

        private fun parts(version: String): List<Int>? =
            versionPattern
                .find(version.trim())
                ?.groupValues
                ?.drop(1)
                ?.map { it.toIntOrNull() ?: return null }
    }
}
