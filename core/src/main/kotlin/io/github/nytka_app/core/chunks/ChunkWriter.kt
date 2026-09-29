package io.github.nytka_app.core.chunks

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ChunkWriter {
    private const val U32_MAX = 0xFFFF_FFFFL

    fun write(chunk: Chunk): ByteArray {
        require(
            chunk.frames.size in 1..ChunkFormat.MAX_FRAMES,
        ) { "A chunk holds 1 to ${ChunkFormat.MAX_FRAMES} frames." }
        require(chunk.firstSeq in 0..U32_MAX && chunk.lastSeq <= U32_MAX) { "Sequence numbers are 32-bit." }

        var size = ChunkFormat.HEADER_SIZE
        chunk.frames.forEachIndexed { i, frame ->
            require(
                frame.seq == chunk.firstSeq + i,
            ) { "Frame $i has sequence ${frame.seq}; expected ${chunk.firstSeq + i}." }
            require(
                frame.capturedAtMs - chunk.baseTimeMs in 0..U32_MAX,
            ) { "Frame $i lies outside the chunk's time range." }
            require(frame.payload.size in 1..0xFFFF) { "Frame $i has ${frame.payload.size} bytes." }
            size += ChunkFormat.recordSize(frame.payload.size)
        }
        require(
            size <= ChunkFormat.MAX_BYTES,
        ) { "The chunk would take $size bytes; the limit is ${ChunkFormat.MAX_BYTES}." }

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ChunkFormat.MAGIC)
        buffer.put(ChunkFormat.VERSION)
        buffer.put(ChunkFormat.OPUS_FS320)
        // RFC 9562 byte order: both halves big-endian, whatever the buffer's order.
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(chunk.session.mostSignificantBits)
        buffer.putLong(chunk.session.leastSignificantBits)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(chunk.firstSeq.toInt())
        buffer.putLong(chunk.baseTimeMs)
        buffer.putInt(chunk.frames.size)
        chunk.frames.forEach { frame ->
            buffer.putInt((frame.capturedAtMs - chunk.baseTimeMs).toInt())
            buffer.putShort(frame.payload.size.toShort())
            buffer.put(frame.payload)
        }
        return buffer.array()
    }
}
