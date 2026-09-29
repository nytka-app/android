package io.github.nytka_app.core.ring

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** One 444-byte ring record as `:pendant` parses it: its sequence number, its stamp and its Opus frames. */
class StoredRecord(
    val ringSeq: Long,
    val stampS: Long,
    val frames: List<ByteArray>,
)

/**
 * The pendant clock against the phone's, taken at one connection: [skewS] is pendant minus phone in seconds,
 * and [writeSeq] the ring's write position when the phone wrote its time. Records below [writeSeq] were
 * stamped before that write.
 */
data class ClockPair(
    val writeSeq: Long,
    val skewS: Long,
)

/**
 * What [CaptureTimes] carries from one batch of records to the next. Only [session], [nextFrame], [lastStampS]
 * and [stale] survive a restart of the app (they live in [RingPosition]); the rest starts empty then.
 */
data class TimeState(
    val session: UUID,
    /** The sequence number the next frame gets in [session]. */
    val nextFrame: Long = 0,
    /** The last record's stamp as read from the ring, before any correction. */
    val lastStampS: Long? = null,
    /** The segment began at a clock restart, so the pendant may have stamped it on a stale clock. */
    val stale: Boolean = false,
    /** Where the last frame ended, in phone milliseconds. */
    val cursorMs: Long? = null,
    /** The first stamp of the run in progress, and whether its rollover has been seen. */
    val runFirstS: Long? = null,
    val anchored: Boolean = false,
)

/** A frame with its capture time and the ring record it came from. Plain class: [payload] is an array. */
class TimedFrame(
    val session: UUID,
    val seq: Long,
    val capturedAtMs: Long,
    val ringSeq: Long,
    val payload: ByteArray,
)

/**
 * The outcome of [CaptureTimes.assign]. [state] is what the next call takes; the counters are for this call only.
 * [skewCorrectedS] is the correction applied to at least one record, [futureRecords] the records dropped for a
 * stamp over 60 s ahead of the phone, [segments] the clock restarts met and [mutedFrames] what [MuteFilter] removed.
 */
data class TimedFrames(
    val frames: List<TimedFrame>,
    val state: TimeState,
    val futureRecords: Int = 0,
    val segments: Int = 0,
    val skewCorrectedS: Long? = null,
    val mutedFrames: Int = 0,
)

/**
 * Where the pendant's frames go, in one transaction with the sync position. [ackedThrough] is the lowest ring
 * sequence any stored frame or chunk still held by the queue (or parked) came from, or the position's
 * `committedNext` when none is: everything below it is safe to `ADVANCE`.
 */
interface StoredSink {
    val ackedThrough: Flow<Long>

    suspend fun position(): RingPosition?

    suspend fun commit(
        frames: List<TimedFrame>,
        position: RingPosition,
    )

    /** Records that `ADVANCE(seq)` went through, so the position's `advanced` is what the pendant holds. */
    suspend fun markAdvanced(seq: Long)

    /** Forgets the position, for a pendant that is paired anew. */
    suspend fun clearPosition()

    suspend fun recordMute(
        atMs: Long,
        muted: Boolean,
    )

    suspend fun muteChanges(): List<MuteChange>
}
