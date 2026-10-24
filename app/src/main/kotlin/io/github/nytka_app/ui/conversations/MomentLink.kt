package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.AudioIndex
import java.time.Instant

/**
 * A link to a moment of a conversation: the route's `at` (epoch milliseconds or an ISO instant) and, when known, the
 * `segment` said then. The segment wins when the transcript still has it; else the paragraph said last at or before
 * the moment. It is focused once, and the playhead moves to it once, each as soon as what it needs is loaded.
 */
internal class MomentLink(
    at: String?,
    segment: String? = null,
) {
    private val moment: Instant? = at?.let(::instantOf)
    private val segmentId: Long? = segment?.toLongOrNull()
    private var focused = false
    private var sought = false

    /** The paragraph to focus, the first time the transcript ([starts], [ids]) is in; else null. */
    fun focus(
        starts: List<Instant>,
        ids: List<Long> = emptyList(),
    ): Int? {
        if (focused) return null
        return paragraph(starts, ids)?.also { focused = true }
    }

    /** Where to move the playhead, the first time both the transcript and the audio [index] are in. */
    fun position(
        starts: List<Instant>,
        index: AudioIndex?,
        ids: List<Long> = emptyList(),
    ): Long? {
        if (sought || index == null) return null
        return paragraph(starts, ids)?.let { index.positionOf(starts[it]) }?.also { sought = true }
    }

    private fun paragraph(
        starts: List<Instant>,
        ids: List<Long>,
    ): Int? {
        if (starts.isEmpty()) return null
        segmentId?.let { ids.indexOf(it) }?.takeIf { it in starts.indices }?.let { return it }
        val at = moment ?: return null
        return starts.indexOfLast { it <= at }.coerceAtLeast(0)
    }

    companion object {
        /** Epoch milliseconds, or an ISO instant such as `2026-10-23T08:42:00Z`; null for anything else. */
        fun instantOf(text: String): Instant? =
            text.toLongOrNull()?.let(Instant::ofEpochMilli) ?: Formatting.parse(text)
    }
}
