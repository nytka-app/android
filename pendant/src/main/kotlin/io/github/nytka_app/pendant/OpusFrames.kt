package io.github.nytka_app.pendant

/** The frames [OpusFrames.toTwentyMs] made, and how many 10 ms frames it had to leave out. */
class JoinedFrames(
    val frames: List<ByteArray>,
    val dropped: Int,
)

/**
 * Makes every Opus packet 20 ms long, which is all the server decodes (320 samples; anything else becomes silence).
 * A recorder that sends 10 ms packets has each pair joined into one packet of two frames: RFC 6716 section 3.2, code 1
 * (two frames of equal size) or code 2 (two frames of different sizes). The frames' bytes are copied, never decoded.
 * Only the mono, code 0 packets the Limitless pendant sends are joined (see [LimitlessPages.isOpusToc]).
 */
object OpusFrames {
    /**
     * 20 ms packets pass through. A 10 ms packet waits for the next one with the same table-of-contents byte; one that
     * is left over (the page ended, a 20 ms packet came, or the next one is another kind) is dropped and counted.
     */
    fun toTwentyMs(frames: List<ByteArray>): JoinedFrames {
        val joiner = OpusJoiner()
        val out = ArrayList<ByteArray>()
        for (frame in frames) joiner.add(frame, out)
        joiner.flush()
        return JoinedFrames(out, joiner.dropped)
    }
}

/** [OpusFrames.toTwentyMs] for a stream: a 10 ms packet waits for its partner across calls of [add] until [flush]. */
class OpusJoiner {
    private var waiting: ByteArray? = null

    var dropped = 0
        private set

    /** Adds [frame]'s 20 ms form to [out], if it has one yet. */
    fun add(
        frame: ByteArray,
        out: MutableList<ByteArray>,
    ) {
        val held = waiting
        val joined = if (frame.isNotEmpty() && held != null && held[0] == frame[0]) join(held, frame) else null
        when {
            frame.isEmpty() -> dropped++
            !isTenMs(frame[0]) -> {
                if (held != null) dropped++
                waiting = null
                out += frame
            }

            joined != null -> {
                out += joined
                waiting = null
            }

            else -> {
                if (held != null) dropped++
                waiting = frame
            }
        }
    }

    /** Gives up on a 10 ms packet still waiting for its partner. */
    fun flush() {
        if (waiting != null) dropped++
        waiting = null
    }

    private fun isTenMs(toc: Byte) = (toc.toInt() shr CONFIG_SHIFT) and CONFIG_LENGTH_MASK == TEN_MS

    /** Two code 0 packets with the same table-of-contents byte as one packet of two frames; null if too long. */
    private fun join(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray? {
        val a = first.copyOfRange(1, first.size)
        val b = second.copyOfRange(1, second.size)
        val toc = first[0].toInt() and CODE_MASK.inv()
        if (a.size == b.size) return byteArrayOf((toc or CODE_TWO_EQUAL).toByte()) + a + b
        if (a.size > MAX_ONE_BYTE_LENGTH) return null
        return byteArrayOf((toc or CODE_TWO_DIFFERENT).toByte(), a.size.toByte()) + a + b
    }

    private companion object {
        const val CONFIG_SHIFT = 3
        const val CONFIG_LENGTH_MASK = 3
        const val TEN_MS = 2
        const val CODE_TWO_EQUAL = 1
        const val CODE_TWO_DIFFERENT = 2
        const val MAX_ONE_BYTE_LENGTH = 251
        const val CODE_MASK = 3
    }
}
