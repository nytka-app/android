package io.github.nytka_app.pendant

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Byte-level fixtures written from `storage.c` and `ring_protocol.dart`, not built with the code under test. */
object RingFixtures {
    fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    fun ack(status: Int) = bytes(0x01, status)

    fun info(
        readSeq: Long,
        writeSeq: Long,
        capacity: Long,
        dropped: Long,
        packetBytes: Int = 444,
    ): ByteArray =
        ByteBuffer
            .allocate(31)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x02)
            .putLong(readSeq)
            .putLong(writeSeq)
            .putInt(capacity.toInt())
            .putLong(dropped)
            .putShort(packetBytes.toShort())
            .array()

    fun readBegin(
        startSeq: Long,
        count: Long,
    ): ByteArray =
        ByteBuffer
            .allocate(13)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x05)
            .putLong(startSeq)
            .putInt(count.toInt())
            .array()

    fun data(payload: ByteArray): ByteArray = byteArrayOf(0x03) + payload

    fun done(
        status: Int,
        nextSeq: Long,
    ): ByteArray =
        ByteBuffer
            .allocate(10)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x04)
            .put(status.toByte())
            .putLong(nextSeq)
            .array()

    /** A 444-byte record: the stamp, then [length][frame] per frame, then [tail] as it is (stale bytes or zeros). */
    fun record(
        stampS: Long,
        frames: List<ByteArray>,
        tail: Int = 0,
        overflowLength: Int? = null,
    ): ByteArray {
        val out = ByteArray(444) { tail.toByte() }
        ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN).putInt(stampS.toInt())
        var offset = 4
        for (frame in frames) {
            out[offset] = frame.size.toByte()
            frame.copyInto(out, offset + 1)
            offset += 1 + frame.size
        }
        if (overflowLength != null) out[offset] = overflowLength.toByte()
        return out
    }

    fun frame(
        size: Int,
        fill: Int,
    ) = ByteArray(size) { fill.toByte() }
}
