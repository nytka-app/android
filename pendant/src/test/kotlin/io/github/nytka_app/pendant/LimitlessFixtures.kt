package io.github.nytka_app.pendant

/** Opus packets and notifications for the Limitless tests: noise behind a real table-of-contents byte. */
object LimitlessFixtures {
    /** CELT wideband, 20 ms, code 0: a packet the pendant sends as it is. */
    const val TWENTY_MS = 0xb8

    /** CELT wideband, 10 ms, code 0: a packet the driver pairs with the next one. */
    const val TEN_MS = 0xb0

    /** The pendant's clock for the first page; later pages add one second each. */
    const val CLOCK_MS = 1_800_000_000_000L

    fun opus(
        toc: Int = TWENTY_MS,
        size: Int = 40,
        fill: Int = 1,
    ): ByteArray = ByteArray(size) { fill.toByte() }.also { it[0] = toc.toByte() }

    /** The notifications of a live message holding [frames], in the order the pendant sends them. */
    fun liveMessage(
        frames: List<ByteArray>,
        index: Int = 1,
        partBytes: Int = 180,
    ) = LimitlessMessages.notifications(index, LimitlessMessages.live(frames), partBytes)

    fun pageStamp(position: Int) = CLOCK_MS + position * 1_000L
}
