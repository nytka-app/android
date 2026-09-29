package io.github.nytka_app.pendant

import java.io.ByteArrayOutputStream

/** One ring record: its sequence number, the pendant clock in whole seconds when it was written, its Opus frames. */
class RingRecord(
    val seq: Long,
    val stampS: Long,
    val frames: List<ByteArray>,
)

/** Splits a 444-byte record into its stamp and Opus frames (`sd_card.c`, `transport.c` `write_to_storage`). */
object RingRecords {
    /**
     * `[stamp u32 big-endian][440 bytes of [length u8][frame]...]`. A zero length byte is padding. The firmware
     * writes a frame's length byte after the last frame that fits and leaves stale bytes behind it, so parsing
     * stops at the first length whose frame would reach the end (`offset + 1 + length >= 440`), as the official
     * app's `parseAudioPayload` does.
     */
    fun parse(
        seq: Long,
        record: ByteArray,
    ): RingRecord {
        require(record.size == RingProtocol.RECORD_BYTES) { "a record is ${RingProtocol.RECORD_BYTES} bytes" }
        var stamp = 0L
        for (i in 0 until RingProtocol.STAMP_BYTES) stamp = (stamp shl 8) or (record[i].toLong() and 0xFF)
        val frames = mutableListOf<ByteArray>()
        var offset = RingProtocol.STAMP_BYTES
        while (offset < record.size - 1) {
            val length = record[offset].toInt() and 0xFF
            when {
                length == 0 -> offset++
                offset + 1 + length >= record.size -> break
                else -> {
                    frames += record.copyOfRange(offset + 1, offset + 1 + length)
                    offset += length + 1
                }
            }
        }
        return RingRecord(seq, stamp, frames)
    }
}

/**
 * Turns DATA notifications, which do not line up with records, back into records. [restart] at every READ_BEGIN:
 * DATA can still arrive after a STOP ACK, and its bytes belong to no window. Not thread-safe.
 */
class RingRecordReassembler {
    private val pending = ByteArrayOutputStream()
    private var nextSeq = 0L

    val pendingBytes: Int get() = pending.size()

    /** Drops buffered bytes; the next complete record is [startSeq]. */
    fun restart(startSeq: Long) {
        pending.reset()
        nextSeq = startSeq
    }

    /** Adds [bytes] and returns the records they complete, in order. */
    fun append(bytes: ByteArray): List<RingRecord> {
        pending.write(bytes)
        if (pending.size() < RingProtocol.RECORD_BYTES) return emptyList()
        val all = pending.toByteArray()
        val whole = all.size / RingProtocol.RECORD_BYTES
        val records =
            List(whole) {
                val start = it * RingProtocol.RECORD_BYTES
                RingRecords.parse(nextSeq + it, all.copyOfRange(start, start + RingProtocol.RECORD_BYTES))
            }
        nextSeq += whole
        pending.reset()
        pending.write(all, whole * RingProtocol.RECORD_BYTES, all.size - whole * RingProtocol.RECORD_BYTES)
        return records
    }
}
