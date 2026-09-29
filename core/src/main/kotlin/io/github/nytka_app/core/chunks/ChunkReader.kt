package io.github.nytka_app.core.chunks

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object ChunkReader {
    fun read(bytes: ByteArray): Chunk {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunk = next(buffer)
        require(!buffer.hasRemaining()) { "The chunk has ${buffer.remaining()} trailing bytes." }
        return chunk
    }

    fun readAll(bytes: ByteArray): List<Chunk> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return buildList { while (buffer.hasRemaining()) add(next(buffer)) }
    }

    private fun next(buffer: ByteBuffer): Chunk =
        try {
            val magic = ByteArray(4).also(buffer::get)
            require(magic.contentEquals(ChunkFormat.MAGIC)) { "The chunk does not start with NYTK." }
            require(buffer.get() == ChunkFormat.VERSION) { "Unsupported chunk version." }
            require(buffer.get() == ChunkFormat.OPUS_FS320) { "Unsupported codec." }
            buffer.order(ByteOrder.BIG_ENDIAN)
            val session = UUID(buffer.long, buffer.long)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val firstSeq = buffer.int.toLong() and 0xFFFF_FFFFL
            val baseTime = buffer.long
            val count = buffer.int
            require(count in 1..ChunkFormat.MAX_FRAMES) { "The chunk holds $count frames." }
            val frames =
                (0 until count).map { i ->
                    val offset = buffer.int.toLong() and 0xFFFF_FFFFL
                    val length = buffer.short.toInt() and 0xFFFF
                    ChunkFrame(firstSeq + i, baseTime + offset, ByteArray(length).also(buffer::get))
                }
            Chunk(session, firstSeq, baseTime, frames)
        } catch (e: BufferUnderflowException) {
            throw IllegalArgumentException("The chunk is truncated.", e)
        }
}
