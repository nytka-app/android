package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.AudioIndex
import java.time.Instant

/**
 * A link to a moment of a conversation: the route's `at`, epoch milliseconds. The paragraph said last at or before it
 * is focused once, and the playhead moves to it once, each as soon as what it needs is loaded.
 */
internal class MomentLink(
    at: String?,
) {
    private val moment: Instant? = at?.toLongOrNull()?.let(Instant::ofEpochMilli)
    private var focused = false
    private var sought = false

    /** The paragraph to focus, the first time the transcript ([starts]) is in; null after that or without a moment. */
    fun focus(starts: List<Instant>): Int? {
        if (focused) return null
        return paragraph(starts)?.also { focused = true }
    }

    /** Where to move the playhead, the first time both the transcript and the audio [index] are in. */
    fun position(
        starts: List<Instant>,
        index: AudioIndex?,
    ): Long? {
        if (sought || index == null) return null
        return paragraph(starts)?.let { index.positionOf(starts[it]) }?.also { sought = true }
    }

    private fun paragraph(starts: List<Instant>): Int? {
        val at = moment ?: return null
        if (starts.isEmpty()) return null
        return starts.indexOfLast { it <= at }.coerceAtLeast(0)
    }
}
