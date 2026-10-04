package io.github.nytka_app.core.chunks

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * The pendant frames of one voice enrollment, kept in memory and never in the upload queue. [body] writes them as
 * chunks of at most [ChunkFormat.MAX_FRAMES] frames (30 s) back to back, as `POST /api/v1/voice/enrollment` takes
 * them. The server refuses more than 120 s, so [add] refuses the frame past [MAX_FRAMES]. Safe to call from the
 * capture thread and the screen at once.
 */
class EnrollmentRecording(
    private val session: UUID = UUID.randomUUID(),
) {
    private val frames = ArrayList<ChunkFrame>()

    @get:Synchronized
    val frameCount: Int get() = frames.size

    val seconds: Double get() = frameCount * ChunkFormat.FRAME_MS / 1000.0

    @get:Synchronized
    val full: Boolean get() = frames.size >= MAX_FRAMES

    /** False, keeping nothing, once the recording holds [MAX_FRAMES]. */
    @Synchronized
    fun add(
        payload: ByteArray,
        capturedAtMs: Long,
    ): Boolean {
        if (frames.size >= MAX_FRAMES) return false
        frames += ChunkFrame(frames.size.toLong(), capturedAtMs, payload)
        return true
    }

    /**
     * How loud the last [window] frames were, 0 to 1. The pendant's Opus is variable-bitrate, so a frame's size follows
     * what it carries: about 8 bytes of silence, 50 to 120 of speech. A meter, not a measurement.
     */
    @Synchronized
    fun level(window: Int = LEVEL_WINDOW): Float {
        if (frames.isEmpty()) return 0f
        val recent = frames.subList(maxOf(0, frames.size - window), frames.size)
        val mean = recent.sumOf { it.payload.size }.toFloat() / recent.size
        return ((mean - QUIET_BYTES) / (LOUD_BYTES - QUIET_BYTES)).coerceIn(0f, 1f)
    }

    /** The request body; empty with no frames. */
    @Synchronized
    fun body(): ByteArray {
        val out = ByteArrayOutputStream()
        frames.chunked(ChunkFormat.MAX_FRAMES).forEach { part ->
            // The phone's clock can step back; the base is the earliest frame so every offset stays positive.
            val base = part.minOf { it.capturedAtMs }
            out.write(ChunkWriter.write(Chunk(session, part.first().seq, base, part)))
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_SECONDS = 120
        const val MAX_FRAMES = MAX_SECONDS * 1000 / ChunkFormat.FRAME_MS
        private const val LEVEL_WINDOW = 10
        private const val QUIET_BYTES = 12f
        private const val LOUD_BYTES = 100f
    }
}
