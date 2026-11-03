package io.github.nytka_app.pendant

/**
 * Live audio from the Limitless pendant's RX notifications: fragments are joined into messages, the Opus packets in
 * them are made 20 ms long ([OpusJoiner]) and stamped. UNTESTED on real hardware. Not for notifications that answer a
 * [LimitlessStorage] operation: the caller keeps those away.
 */
internal class LimitlessLiveAudio(
    private val now: () -> Long,
) {
    private val reassembler = LimitlessReassembler()
    private val joiner = OpusJoiner()

    /** How many 10 ms packets were left without a partner. */
    @get:Synchronized
    val dropped: Int
        get() = joiner.dropped

    /**
     * The frames [raw] completes, none for a fragment or for something that is not audio. A message holds several
     * frames and has one arrival time, so the earlier frames are stamped back from it.
     */
    @Synchronized
    fun accept(raw: ByteArray): List<AudioFrame> {
        val packet = LimitlessProtocol.parsePacket(raw) ?: return emptyList()
        val message = reassembler.add(packet) ?: return emptyList()
        val payloads = ArrayList<ByteArray>()
        for (frame in LimitlessPages.extractFrames(message)) joiner.add(frame, payloads)
        val at = now()
        return payloads.mapIndexed { i, payload -> AudioFrame(payload, at - (payloads.size - 1 - i) * FRAME_MS) }
    }

    /** A new connection or a new mode: what is half joined belongs to the old stream. */
    @Synchronized
    fun reset() {
        reassembler.reset()
        joiner.flush()
    }

    private companion object {
        const val FRAME_MS = 20L
    }
}
