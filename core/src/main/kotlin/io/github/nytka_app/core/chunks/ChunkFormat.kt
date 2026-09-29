package io.github.nytka_app.core.chunks

import java.util.UUID

/** `application/vnd.nytka.frames.v1`, fixed by docs/specs/v0.1.md in nytka-app/server. */
object ChunkFormat {
    const val MEDIA_TYPE = "application/vnd.nytka.frames.v1"
    const val VERSION: Byte = 1
    const val OPUS_FS320: Byte = 21
    const val HEADER_SIZE = 38
    const val RECORD_HEADER_SIZE = 6
    const val MAX_FRAMES = 1500
    const val MAX_BYTES = 512 * 1024
    const val FRAME_MS = 20

    val MAGIC = byteArrayOf('N'.code.toByte(), 'Y'.code.toByte(), 'T'.code.toByte(), 'K'.code.toByte())

    fun recordSize(payloadSize: Int) = RECORD_HEADER_SIZE + payloadSize
}

class ChunkFrame(
    val seq: Long,
    val capturedAtMs: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?) =
        other is ChunkFrame &&
            seq == other.seq &&
            capturedAtMs == other.capturedAtMs &&
            payload.contentEquals(other.payload)

    override fun hashCode() = (seq.hashCode() * 31 + capturedAtMs.hashCode()) * 31 + payload.contentHashCode()

    override fun toString() = "ChunkFrame(seq=$seq, capturedAtMs=$capturedAtMs, ${payload.size} bytes)"
}

data class Chunk(
    val session: UUID,
    val firstSeq: Long,
    val baseTimeMs: Long,
    val frames: List<ChunkFrame>,
) {
    val lastSeq: Long get() = firstSeq + frames.size - 1
}
