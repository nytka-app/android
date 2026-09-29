package io.github.nytka_app.capture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.chunks.Chunk
import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.chunks.ChunkFrame
import io.github.nytka_app.core.chunks.ChunkWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.inject.Inject

object FrameRecorder {
    suspend fun record(
        frames: Flow<CapturedFrame>,
        durationMs: Long,
    ): List<CapturedFrame> {
        val recorded = mutableListOf<CapturedFrame>()
        withTimeoutOrNull(durationMs) { frames.collect { recorded += it } }
        return recorded
    }
}

/** Frames in the chunk format, back to back: what `ChunkReader.readAll` and `Nytka.Replay` read. */
object FixtureWriter {
    fun write(frames: List<CapturedFrame>): ByteArray {
        val output = ByteArrayOutputStream()
        var run = mutableListOf<CapturedFrame>()
        var size = 0

        fun flush() {
            if (run.isEmpty()) return
            val first = run.first()
            output.write(
                ChunkWriter.write(
                    Chunk(
                        first.session,
                        first.seq,
                        first.capturedAtMs,
                        run.map {
                            ChunkFrame(it.seq, it.capturedAtMs, it.payload)
                        },
                    ),
                ),
            )
            run = mutableListOf()
        }

        frames.forEach { frame ->
            val record = ChunkFormat.recordSize(frame.payload.size)
            val fits =
                run.isNotEmpty() &&
                    run.size < ChunkFormat.MAX_FRAMES &&
                    frame.session == run.last().session &&
                    frame.seq == run.last().seq + 1 &&
                    frame.capturedAtMs >= run.first().capturedAtMs &&
                    size + record <= ChunkFormat.MAX_BYTES
            if (!fits) {
                flush()
                size = ChunkFormat.HEADER_SIZE
            }
            run += frame
            size += record
        }
        flush()
        return output.toByteArray()
    }
}

interface FixtureRecorder {
    /** Records the next minute of frames and returns where the fixture was saved. */
    suspend fun record(frames: Flow<CapturedFrame>): String
}

class AndroidFixtureRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FixtureRecorder {
        override suspend fun record(frames: Flow<CapturedFrame>): String {
            val recorded = FrameRecorder.record(frames, DURATION_MS)
            check(recorded.isNotEmpty()) { "No frames arrived in 60 seconds. Is the pendant connected and live?" }
            val directory = File(context.getExternalFilesDir(null), "fixtures").apply { mkdirs() }
            val file = File(directory, "frames-${Instant.now().toString().replace(':', '-')}.nytk")
            withContext(Dispatchers.IO) { file.writeBytes(FixtureWriter.write(recorded)) }
            return file.absolutePath
        }

        private companion object {
            const val DURATION_MS = 60_000L
        }
    }
