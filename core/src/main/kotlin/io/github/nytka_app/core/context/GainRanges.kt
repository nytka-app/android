package io.github.nytka_app.core.context

/**
 * Turns the pendant's reported microphone gain level (0 to 8) into [RangeKind.GAIN] ranges: one for each stretch the
 * level held while the pendant was connected, with the level as the detail, so the server can compare audio levels and
 * voice scores across a change. A stretch over [ContextRangeTracker.MAX_MS] is sent as consecutive pieces. It records,
 * never sets, the gain. Pure; the caller feeds it from one coroutine.
 */
class GainRanges(
    private val clock: () -> Long,
    private val emit: (ContextRange) -> Unit,
) {
    private var level: Int? = null
    private var since = 0L

    /** The level in effect now, or null when the pendant is away or does not report one. */
    fun onGain(next: Int?) {
        val now = clock()
        if (next == level) return
        close(now)
        level = next
        since = now
    }

    /** Capture stops: the open stretch ends now. */
    fun onStop() = onGain(null)

    private fun close(now: Long) {
        val current = level ?: return
        var start = since
        while (now - start > ContextRangeTracker.MAX_MS) {
            emit(range(current, start, start + ContextRangeTracker.MAX_MS))
            start += ContextRangeTracker.MAX_MS
        }
        if (now - start >= ContextRangeTracker.MIN_MS) emit(range(current, start, now))
    }

    private fun range(
        level: Int,
        start: Long,
        end: Long,
    ) = ContextRange(RangeKind.GAIN, Route.OTHER, start, end, level.toString())
}
