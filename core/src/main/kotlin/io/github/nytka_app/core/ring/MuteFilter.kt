package io.github.nytka_app.core.ring

/** One change of the mute setting: from the button, the notification or the Device tab. */
data class MuteChange(
    val atMs: Long,
    val muted: Boolean,
)

/**
 * Drops the stored frames a mute covers. The pendant records while the phone is away whatever the mute says, so
 * a muted stretch (from a change to muted until the next change, open-ended for the last one) removes every frame
 * within [marginMs] of it. Before the first change nothing is muted.
 */
class MuteFilter(
    changes: List<MuteChange>,
    private val marginMs: Long = MARGIN_MS,
) {
    private val stretches: List<LongRange> = stretchesOf(changes.sortedBy { it.atMs })

    fun covers(timeMs: Long): Boolean = stretches.any { timeMs in it }

    /**
     * Removes the covered frames and numbers the rest again without holes, since a hole in a session makes the
     * server wait 10 minutes. [before] is the state the batch started from.
     */
    fun apply(
        timed: TimedFrames,
        before: TimeState,
    ): TimedFrames {
        if (stretches.isEmpty()) return timed
        val next = mutableMapOf(before.session to before.nextFrame)
        val kept = mutableListOf<TimedFrame>()
        timed.frames.forEach { frame ->
            if (covers(frame.capturedAtMs)) return@forEach
            val seq = next[frame.session] ?: 0L
            next[frame.session] = seq + 1
            kept += TimedFrame(frame.session, seq, frame.capturedAtMs, frame.ringSeq, frame.payload)
        }
        val session = timed.state.session
        val nextFrame = next[session] ?: if (session == before.session) before.nextFrame else 0L
        return timed.copy(
            frames = kept,
            state = timed.state.copy(nextFrame = nextFrame),
            mutedFrames = timed.mutedFrames + timed.frames.size - kept.size,
        )
    }

    private fun stretchesOf(sorted: List<MuteChange>): List<LongRange> {
        val result = mutableListOf<LongRange>()
        var start: Long? = null
        sorted.forEach { change ->
            val open = start
            if (change.muted && open == null) {
                start = change.atMs
            } else if (!change.muted && open != null) {
                result += (open - marginMs)..(change.atMs + marginMs)
                start = null
            }
        }
        start?.let { result += (it - marginMs)..Long.MAX_VALUE }
        return result
    }

    companion object {
        const val MARGIN_MS = 2_000L
    }
}
