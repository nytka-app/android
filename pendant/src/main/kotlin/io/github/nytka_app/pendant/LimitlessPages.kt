package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessWire.LENGTH_DELIMITED
import io.github.nytka_app.pendant.LimitlessWire.TAG_FIELD_1
import io.github.nytka_app.pendant.LimitlessWire.VARINT
import io.github.nytka_app.pendant.LimitlessWire.fits
import io.github.nytka_app.pendant.LimitlessWire.readVarint
import io.github.nytka_app.pendant.LimitlessWire.skipField

/**
 * Flash pages and live audio messages of the Limitless pendant: where the Opus packets sit and how they are pulled
 * out. Ported from `BasedHardware/omi` at afad3b7 (MIT licensed, license in that repository):
 * `app/android/app/src/main/kotlin/com/friend/ios/limitless/LimitlessProtocol.kt` (`parseStorageBuffer`,
 * `extractOpusFrames`) and `LimitlessFlashDrainEngine.kt`. UNTESTED on real hardware.
 *
 * The parsers never throw: a frame they cannot read is left out, and a page that holds audio they cannot extract
 * is [parsePages]'s `null`, so that nothing frees audio that was not read.
 */
object LimitlessPages {
    private const val TAG_PAGE_SEQ = 0x10
    private const val TAG_AUDIO_WRAPPER = 0x1a
    private const val TAG_AUDIO_DATA = 0x12

    private const val STORAGE_BUFFER_FIELD = 2
    private const val STORAGE_INDEX = 5
    private const val STORAGE_PAGE = 6

    private const val MIN_FRAME = 10
    private const val MAX_FRAME = 200

    private val TOC_BYTES = setOf(0xb8, 0x78, 0xf8, 0xb0, 0x70, 0xf0)

    /** The index of a storage buffer that carries none. */
    const val UNREADABLE = -1L

    /** The pages of a message that arrived whole: its field 2 holds a storage buffer each; see [pageOf]. */
    fun parsePages(payload: ByteArray): List<LimitlessPage?> {
        val pages = ArrayList<LimitlessPage?>()
        var pos = 0
        while (pos < payload.size) {
            val wire = payload[pos].toInt() and 0x07
            val number = (payload[pos++].toInt() and 0xFF) shr 3
            when (wire) {
                LENGTH_DELIMITED -> {
                    val (length, next) = readVarint(payload, pos)
                    if (!fits(length, next, payload.size)) break
                    if (number == STORAGE_BUFFER_FIELD) {
                        pages += pageOf(payload.copyOfRange(next, next + length.toInt()))
                    }
                    pos = next + length.toInt()
                }

                VARINT -> pos = readVarint(payload, pos).second
            }
        }
        return pages
    }

    /**
     * A storage buffer (2 session, 5 index, 6 the page). Null when the page holds audio that could not be extracted:
     * it must end the drain and never be freed. A page without audio gives no frames, so the drain can pass it.
     * Buffers with no page, or no index, are skipped by the drain, so they appear here as no page at all.
     */
    private fun pageOf(buffer: ByteArray): LimitlessPage? {
        var pos = 0
        var index: Long? = null
        var page: ByteArray? = null
        while (pos < buffer.size) {
            val wire = buffer[pos].toInt() and 0x07
            val number = (buffer[pos++].toInt() and 0xFF) shr 3
            if (wire == VARINT) {
                val (value, next) = readVarint(buffer, pos)
                pos = next
                if (number == STORAGE_INDEX) index = value
            } else if (wire == LENGTH_DELIMITED) {
                val (length, next) = readVarint(buffer, pos)
                if (!fits(length, next, buffer.size)) break
                if (number == STORAGE_PAGE) page = buffer.copyOfRange(next, next + length.toInt())
                pos = next + length.toInt()
            }
        }
        val data = page?.takeIf { it.isNotEmpty() } ?: return LimitlessPage(index ?: UNREADABLE, 0, emptyList())
        val frames = extractFrames(data)
        if (frames.isEmpty() && hasAudio(data)) return null
        return LimitlessPage(index ?: UNREADABLE, pageTimeMs(data), frames)
    }

    /** The pendant's clock when the page was written: field 1 at its start. */
    fun pageTimeMs(page: ByteArray): Long =
        if (page.isNotEmpty() && page[0].toInt() == TAG_FIELD_1) readVarint(page, 1).first else 0L

    private fun skipHeader(page: ByteArray): Int {
        var pos = 0
        if (pos < page.size && page[pos].toInt() == TAG_FIELD_1) pos = readVarint(page, pos + 1).second
        if (pos < page.size && page[pos].toInt() == TAG_PAGE_SEQ) pos = readVarint(page, pos + 1).second
        return pos
    }

    /** The Opus packets of a flash page or of a live message: they sit in `0x1a` wrappers, in `0x12` audio fields. */
    fun extractFrames(page: ByteArray): List<ByteArray> {
        val frames = ArrayList<ByteArray>()
        var pos = skipHeader(page)
        while (pos < page.size - 2) {
            pos = if (page[pos].toInt() == TAG_AUDIO_WRAPPER) wrapperAt(page, pos, frames) else pos + 1
        }
        return frames
    }

    /** The wrapper at [at]: its audio goes to [frames]; the position after it, or the end for a bad length. */
    private fun wrapperAt(
        page: ByteArray,
        at: Int,
        frames: MutableList<ByteArray>,
    ): Int {
        val (length, next) = readVarint(page, at + 1)
        if (!fits(length, next, page.size)) return page.size
        val end = next + length.toInt()
        var pos = next
        while (pos < end - 1) pos = audioField(page, pos, end, frames)
        return end
    }

    /** One field of a wrapper that ends at [end]; the position after it. */
    private fun audioField(
        page: ByteArray,
        at: Int,
        end: Int,
        frames: MutableList<ByteArray>,
    ): Int {
        val marker = page[at].toInt() and 0xFF
        if (marker == TAG_FIELD_1) return readVarint(page, at + 1).second
        if (marker == TAG_AUDIO_DATA) {
            val (length, next) = readVarint(page, at + 1)
            if (!fits(length, next, page.size)) return end
            collect(page, next, next + length.toInt(), frames)
            return next + length.toInt()
        }
        return when (marker and 0x07) {
            VARINT -> readVarint(page, at + 1).second
            LENGTH_DELIMITED -> {
                val (length, next) = readVarint(page, at + 1)
                if (!fits(length, next, end)) end else next + length.toInt()
            }

            else -> at + 1
        }
    }

    private fun collect(
        data: ByteArray,
        start: Int,
        end: Int,
        frames: MutableList<ByteArray>,
    ) {
        var pos = start
        while (pos < end - 1) {
            val wire = data[pos++].toInt() and 0x07
            if (wire == VARINT) {
                pos = readVarint(data, pos).second
            } else if (wire == LENGTH_DELIMITED) {
                val (length, next) = readVarint(data, pos)
                if (!fits(length, next, end)) return
                val size = length.toInt()
                if (size in MIN_FRAME..MAX_FRAME && isOpusToc(data[next])) {
                    frames += data.copyOfRange(next, next + size)
                } else if (size > MIN_FRAME) {
                    collect(data, next, next + size, frames)
                }
                pos = next + size
            } else {
                return
            }
        }
    }

    /** Whether the page has an audio field in a wrapper; what it cannot read counts as audio, so it is never passed. */
    fun hasAudio(page: ByteArray): Boolean {
        var pos = skipHeader(page)
        while (pos < page.size - 2) {
            if (page[pos].toInt() == TAG_AUDIO_WRAPPER) {
                val (length, next) = readVarint(page, pos + 1)
                if (!fits(length, next, page.size)) return false
                val end = next + length.toInt()
                pos = next
                while (pos < end - 1) {
                    if ((page[pos].toInt() and 0xFF) == TAG_AUDIO_DATA) return true
                    pos = skipField(page, pos, end) ?: return true
                }
                pos = end
            } else {
                pos = skipField(page, pos, page.size) ?: return true
            }
        }
        return false
    }

    /** A 10 or 20 ms Opus packet at 16 kHz in code 0 (the table-of-contents bytes the pendant sends). */
    fun isOpusToc(byte: Byte): Boolean = (byte.toInt() and 0xFF) in TOC_BYTES
}
