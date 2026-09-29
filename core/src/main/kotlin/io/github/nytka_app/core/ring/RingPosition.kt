package io.github.nytka_app.core.ring

import java.util.UUID

/**
 * How far the paired pendant's ring has been copied into Room. Written with the frames it covers, in one
 * transaction, so a restart resumes at [committedNext] with nothing repeated and nothing skipped.
 *
 * Ring sequence numbers restart when the ring is cleared; [epoch] counts those restarts, so a frame or chunk
 * from an old epoch never holds back `ADVANCE` in the new one.
 */
data class RingPosition(
    val pendant: String,
    val committedNext: Long,
    val epoch: Long = 0,
    /** The last `ADVANCE` the pendant answered. */
    val advanced: Long = 0,
    val session: UUID? = null,
    val nextFrame: Long = 0,
    /** `droppedPackets` at the last INFO, to notice a ring that was cleared and refilled. */
    val lastDropped: Long = 0,
    val lastStampS: Long? = null,
    val clock: ClockPair? = null,
    val staleClock: Boolean = false,
) {
    /** A new sync run (each connection): a new session, numbering from 0. */
    fun newRun(session: UUID = UUID.randomUUID()): TimeState =
        TimeState(session = session, lastStampS = lastStampS, stale = staleClock)

    /** Carries on the session the last commit ended in, for a retry within one connection or a restart. */
    fun continueRun(): TimeState =
        TimeState(
            session = session ?: UUID.randomUUID(),
            nextFrame = if (session == null) 0 else nextFrame,
            lastStampS = lastStampS,
            stale = staleClock,
        )

    /** The position once [timed] is committed and the ring is read up to (not including) [next]. */
    fun after(
        timed: TimedFrames,
        next: Long,
    ): RingPosition =
        copy(
            committedNext = next,
            session = timed.state.session,
            nextFrame = timed.state.nextFrame,
            lastStampS = timed.state.lastStampS,
            staleClock = timed.state.stale,
        )

    /** INFO shows the ring was cleared: it ends before what was read, or it dropped fewer packets than before. */
    fun wasCleared(
        writeSeq: Long,
        droppedPackets: Long,
    ): Boolean = writeSeq < committedNext || droppedPackets < lastDropped

    /**
     * A clear followed by a refill past [committedNext] with nothing dropped shows only in the stamps: the first
     * record read is stamped over 2 s before the last committed one. Check it before assigning times, since
     * [CaptureTimes] would take the same step for a clock restart.
     */
    fun wasRefilled(firstStampS: Long): Boolean = lastStampS != null && firstStampS < lastStampS - RingTimes.RESTART_S

    /** The ring starts over at [readSeq]: sequence numbers restart, so the epoch moves on. */
    fun newEpoch(
        readSeq: Long,
        droppedPackets: Long,
    ): RingPosition =
        copy(
            committedNext = readSeq,
            epoch = epoch + 1,
            advanced = readSeq,
            session = null,
            nextFrame = 0,
            lastDropped = droppedPackets,
            lastStampS = null,
            clock = null,
            staleClock = false,
        )
}
