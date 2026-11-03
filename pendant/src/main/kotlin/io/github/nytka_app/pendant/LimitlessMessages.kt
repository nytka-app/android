package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessWire.intField
import io.github.nytka_app.pendant.LimitlessWire.message

/**
 * What a Limitless pendant sends, as the bytes [LimitlessProtocol] reads, for [FakeLimitless] and for tests. Built from
 * the same reading of `BasedHardware/omi` at afad3b7 as the parsers, so a round trip proves them consistent, not
 * right: UNTESTED on real hardware.
 */
object LimitlessMessages {
    private const val PAGE_TIME = 1
    private const val PAGE_SEQ = 2
    private const val PAGE_AUDIO = 3
    private const val AUDIO_DATA = 2
    private const val AUDIO_FRAME = 1

    private const val STORAGE_BUFFER = 2
    private const val STORAGE_SESSION = 2
    private const val STORAGE_INDEX = 5
    private const val STORAGE_PAGE = 6

    private const val STATUS_OUTER = 5
    private const val BUTTON = 8
    private const val BUTTON_DOUBLE = 3L

    /** A flash page: the pendant's clock, then one wrapper of audio per frame. */
    fun page(
        timestampMs: Long,
        frames: List<ByteArray>,
    ): ByteArray =
        frames.fold(intField(PAGE_TIME, timestampMs) + intField(PAGE_SEQ, 0)) { page, frame -> page + audio(frame) }

    private fun audio(frame: ByteArray) = message(PAGE_AUDIO, message(AUDIO_DATA, message(AUDIO_FRAME, frame)))

    /** The payload of one download message: a storage buffer for each of [pages] (index to page bytes). */
    fun pages(
        session: Long,
        pages: List<Pair<Long, ByteArray>>,
    ): ByteArray =
        pages.fold(ByteArray(0)) { out, (index, page) ->
            val buffer =
                intField(STORAGE_SESSION, session) + intField(STORAGE_INDEX, index) + message(STORAGE_PAGE, page)
            out + message(STORAGE_BUFFER, buffer)
        }

    /** The payload of a live message holding [frames]. */
    fun live(frames: List<ByteArray>): ByteArray = frames.fold(ByteArray(0)) { out, frame -> out + audio(frame) }

    /** The payload of a double press. */
    fun doublePress(): ByteArray = message(BUTTON, intField(1, BUTTON_DOUBLE))

    /** The payload of the answer to `GetDeviceStatus`. */
    fun flash(flash: LimitlessFlash): ByteArray {
        val counters =
            intField(1, flash.oldest) +
                intField(2, flash.newest) +
                intField(3, flash.session) +
                intField(4, flash.free) +
                intField(5, flash.total)
        return message(STATUS_OUTER, message(STATUS_OUTER, counters))
    }

    /** The notifications that carry [payload] as message [index], at most [partBytes] of it in each. */
    fun notifications(
        index: Int,
        payload: ByteArray,
        partBytes: Int,
    ): List<ByteArray> {
        val parts =
            if (payload.isEmpty()) {
                listOf(payload)
            } else {
                payload.indices.step(partBytes).map { payload.copyOfRange(it, minOf(it + partBytes, payload.size)) }
            }
        return parts.mapIndexed { seq, part -> LimitlessProtocol.wrap(index, seq, parts.size, part) }
    }

    /** The first message of a command written to TX: its number and its numeric fields. */
    fun command(raw: ByteArray): Pair<Int, Map<Int, Long>>? {
        val payload = LimitlessProtocol.parsePacket(raw)?.payload ?: return null
        val (tag, afterTag) = LimitlessWire.readVarint(payload, 0)
        if (tag and WIRE_MASK != LENGTH_DELIMITED) return null
        val (length, start) = LimitlessWire.readVarint(payload, afterTag)
        if (length < 0 || start + length > payload.size) return null
        val body = payload.copyOfRange(start, start + length.toInt())
        val numbers = HashMap<Int, Long>()
        var pos = 0
        while (pos < body.size) {
            val (field, next) = LimitlessWire.readVarint(body, pos)
            if (field and WIRE_MASK != 0L) break
            val (value, after) = LimitlessWire.readVarint(body, next)
            numbers[(field shr 3).toInt()] = value
            pos = after
        }
        return (tag shr 3).toInt() to numbers
    }

    private const val WIRE_MASK = 7L
    private const val LENGTH_DELIMITED = 2L
}
