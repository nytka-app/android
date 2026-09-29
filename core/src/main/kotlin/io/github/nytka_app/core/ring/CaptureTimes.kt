package io.github.nytka_app.core.ring

import java.util.UUID
import kotlin.math.abs

/** Constants of the ring's clock, in seconds unless the name says otherwise. */
object RingTimes {
    /** A stamp this far below the previous record's marks a clock restart. */
    const val RESTART_S = 2L

    /** Records whose stamps differ by at most this make one run. */
    const val RUN_S = 1L

    /** A record stamped further ahead of the phone's clock than this is dropped. */
    const val FUTURE_S = 60L

    /** A skew up to this is left alone. */
    const val SKEW_LIMIT_S = 5L

    /** One Opus frame is 20 ms of audio. */
    const val FRAME_MS = 20L
}

/** Turns ring records into frames with capture times and sequence numbers, in ring order. */
fun interface CaptureTimes {
    /**
     * [records] follow [state] in ring order; the result's state is what the next batch takes. [clock] is the
     * pendant clock's skew at this connection (see [ClockPair]), [nowMs] the phone's clock. Call it with whole
     * batches: a run of stamps cut by the end of a batch is timed from what the batch holds.
     */
    fun assign(
        records: List<StoredRecord>,
        state: TimeState,
        clock: ClockPair?,
        nowMs: Long,
    ): TimedFrames
}

/**
 * The rule of the v0.3 spec, "Capture times". A stamp is the pendant's clock when the SD worker wrote the
 * record, cut to the second. A run is a stretch of records whose stamps differ by at most 1 s; the record whose
 * stamp first exceeds the run's first one ends at its stamp, so the frames up to and in it are back-dated from
 * there in 20 ms steps. After that, and in a run that never rolls over, a record starts at the later of the
 * cursor (where the last frame ended) and its stamp. Times never run backwards.
 *
 * A stamp over 2 s below the previous record's is a clock restart and begins a new segment with a new session
 * from [newSession]. After a restart the pendant ran on the last clock the phone wrote, so when the connection's
 * skew exceeds 5 s it is subtracted from the stamps of the segment's records below the clock pair's `writeSeq`.
 * Records stamped over 60 s ahead of the phone are dropped and counted.
 *
 * The caller must not overwrite the persisted skew pair ([RingPosition.clock]) with a newer one while stale
 * records (a restart segment below the old pair's `writeSeq`) remain unread: they still need the old skew.
 */
class RingCaptureTimes(
    private val newSession: () -> UUID = UUID::randomUUID,
) : CaptureTimes {
    override fun assign(
        records: List<StoredRecord>,
        state: TimeState,
        clock: ClockPair?,
        nowMs: Long,
    ): TimedFrames = Assignment(state, clock, nowMs, newSession).run(records)

    private class Assignment(
        initial: TimeState,
        private val clock: ClockPair?,
        private val nowMs: Long,
        private val newSession: () -> UUID,
    ) {
        private var session = initial.session
        private var nextFrame = initial.nextFrame
        private var lastStampS = initial.lastStampS
        private var stale = initial.stale
        private var cursorMs = initial.cursorMs
        private var runFirstS = initial.runFirstS
        private var anchored = initial.anchored

        private val frames = mutableListOf<TimedFrame>()
        private val pending = mutableListOf<Pair<StoredRecord, Long>>()
        private var futureRecords = 0
        private var segments = 0
        private var correctedS: Long? = null

        fun run(records: List<StoredRecord>): TimedFrames {
            records.forEach(::take)
            flush()
            val state =
                TimeState(session, nextFrame, lastStampS, stale, cursorMs, runFirstS, anchored)
            return TimedFrames(frames, state, futureRecords, segments, correctedS)
        }

        private fun take(record: StoredRecord) {
            val last = lastStampS
            val restart = last != null && record.stampS < last - RingTimes.RESTART_S
            val correction = correctionFor(record, restart)
            val stamp = record.stampS - correction
            if (stamp * MS > nowMs + RingTimes.FUTURE_S * MS) {
                futureRecords++
                return
            }
            if (restart) startSegment()
            if (correction != 0L) correctedS = correction
            lastStampS = record.stampS
            joinRun(stamp)
            when {
                anchored -> place(record, stamp)
                stamp > (runFirstS ?: stamp) -> {
                    pending += record to stamp
                    anchor(stamp)
                }
                else -> pending += record to stamp
            }
        }

        /** The skew to subtract from [record]'s stamp: only in a segment after a restart, before the clock write. */
        private fun correctionFor(
            record: StoredRecord,
            restart: Boolean,
        ): Long {
            val pair = clock ?: return 0
            val stamped = (stale || restart) && record.ringSeq < pair.writeSeq
            return if (stamped && abs(pair.skewS) > RingTimes.SKEW_LIMIT_S) pair.skewS else 0
        }

        /** A stamp more than a second past the run's first begins a new run. */
        private fun joinRun(stamp: Long) {
            val first = runFirstS
            if (first == null || stamp - first > RingTimes.RUN_S) {
                flush()
                runFirstS = stamp
                anchored = false
            }
        }

        private fun startSegment() {
            flush()
            session = newSession()
            nextFrame = 0
            cursorMs = null
            runFirstS = null
            anchored = false
            stale = true
            segments++
        }

        /** The pending records end at [stamp]: back-date them from there. */
        private fun anchor(stamp: Long) {
            val count = pending.sumOf { it.first.frames.size }
            val earliest = stamp * MS - count * RingTimes.FRAME_MS
            var start = cursorMs?.let { maxOf(it, earliest) } ?: earliest
            pending.forEach { (record, _) -> start = emit(record, start) }
            pending.clear()
            anchored = true
        }

        /** An unanchored run's records are left-aligned at their stamps. */
        private fun flush() {
            pending.forEach { (record, stamp) -> place(record, stamp) }
            pending.clear()
        }

        private fun place(
            record: StoredRecord,
            stamp: Long,
        ) {
            emit(record, cursorMs?.let { maxOf(it, stamp * MS) } ?: (stamp * MS))
        }

        /** Emits [record]'s frames from [startMs]; returns where the last one ends. */
        private fun emit(
            record: StoredRecord,
            startMs: Long,
        ): Long {
            record.frames.forEachIndexed { k, payload ->
                frames += TimedFrame(session, nextFrame++, startMs + k * RingTimes.FRAME_MS, record.ringSeq, payload)
            }
            val end = startMs + record.frames.size * RingTimes.FRAME_MS
            if (record.frames.isNotEmpty()) cursorMs = end
            return end
        }
    }

    private companion object {
        const val MS = 1_000L
    }
}
