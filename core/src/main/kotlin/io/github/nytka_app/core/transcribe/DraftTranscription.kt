package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.chunks.ChunkFrame
import io.github.nytka_app.core.chunks.ChunkReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What one run did. Counts and durations only: nothing here may carry what was said. */
data class DraftRunOutcome(
    val chunks: Int = 0,
    val segments: Int = 0,
    val failedSegments: Int = 0,
    val unreadableChunks: Int = 0,
    val audioMs: Long = 0,
    val speechMs: Long = 0,
    val workMs: Long = 0,
) {
    /** Recognition time over speech time; below 1 is faster than real time. */
    val realTimeFactor: Double? get() = if (speechMs > 0) workMs.toDouble() / speechMs else null

    operator fun plus(other: DraftRunOutcome) =
        DraftRunOutcome(
            chunks + other.chunks,
            segments + other.segments,
            failedSegments + other.failedSegments,
            unreadableChunks + other.unreadableChunks,
            audioMs + other.audioMs,
            speechMs + other.speechMs,
            workMs + other.workMs,
        )
}

/**
 * Turns the waiting copies in `draft_audio` into draft text, oldest first. A group is up to [GROUP_ROWS] copies of
 * one capture session without a gap; its speech is found by [SpeechSegments], decoded, recognised and saved in one
 * transaction with the deletion of the group's copies, so a run stopped halfway repeats the group and loses nothing.
 */
class DraftTranscription(
    private val dao: DraftsDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val clock: () -> Long = System::nanoTime,
) {
    /** Drops draft text older than [KEEP_TEXT_MS]. */
    suspend fun prune() = dao.deleteTextsBefore(now() - KEEP_TEXT_MS)

    /** Works through groups while [keepGoing] answers true before each; returns what it did. */
    suspend fun run(
        transcriber: OnDeviceTranscriber,
        decoder: OpusDecoder,
        model: String,
        keepGoing: suspend () -> Boolean,
    ): DraftRunOutcome {
        var total = DraftRunOutcome()
        while (keepGoing()) {
            val group = nextGroup() ?: break
            total += transcribe(group, transcriber, decoder, model)
        }
        return total
    }

    private suspend fun nextGroup(): List<DraftAudioRow>? {
        val rows = dao.oldestAudio(GROUP_ROWS)
        if (rows.isEmpty()) return null
        var end = 1
        while (end < rows.size &&
            rows[end].session == rows[0].session &&
            rows[end].firstSeq == rows[end - 1].firstSeq + rows[end - 1].frameCount
        ) {
            end++
        }
        return rows.subList(0, end)
    }

    @Suppress("TooGenericExceptionCaught") // one bad stretch must not stop the rest of the day
    private suspend fun transcribe(
        group: List<DraftAudioRow>,
        transcriber: OnDeviceTranscriber,
        decoder: OpusDecoder,
        model: String,
    ): DraftRunOutcome {
        var unreadable = 0
        val frames =
            group.flatMap { row ->
                runCatching { ChunkReader.read(row.body).frames }.getOrElse {
                    unreadable++
                    emptyList()
                }
            }
        val texts = mutableListOf<DraftTextRow>()
        var failed = 0
        var speechMs = 0L
        val started = clock()
        for (range in SpeechSegments.find(frames)) {
            currentCoroutineContext().ensureActive()
            speechMs += range.count() * ChunkFormat.FRAME_MS.toLong()
            try {
                val text = transcriber.transcribe(decode(frames, range, decoder)).trim()
                if (text.isNotEmpty()) texts += draft(group[0].session, frames, range, text, model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }
        val workMs = (clock() - started) / NANOS_PER_MS
        dao.finish(texts, group.map { it.id })
        return DraftRunOutcome(
            chunks = group.size,
            segments = texts.size,
            failedSegments = failed,
            unreadableChunks = unreadable,
            audioMs = frames.size * ChunkFormat.FRAME_MS.toLong(),
            speechMs = speechMs,
            workMs = workMs,
        )
    }

    private fun draft(
        session: String,
        frames: List<ChunkFrame>,
        range: IntRange,
        text: String,
        model: String,
    ) = DraftTextRow(
        session = session,
        firstSeq = frames[range.first].seq,
        // A frame's time is when its last fragment arrived, the end of its 20 ms.
        startMs = frames[range.first].capturedAtMs - ChunkFormat.FRAME_MS,
        endMs = frames[range.last].capturedAtMs,
        text = text,
        model = model,
        createdAtMs = now(),
    )

    companion object {
        /** About 5 minutes of audio: what one check of [run]'s `keepGoing` may cost. */
        const val GROUP_ROWS = 10

        /** Frames decoded and thrown away before a segment, so the decoder has settled: 80 ms. */
        const val PREROLL_FRAMES = 4

        const val KEEP_TEXT_MS = 7L * 24 * 60 * 60 * 1000
        private const val NANOS_PER_MS = 1_000_000

        /** The samples of [range], decoded after up to [PREROLL_FRAMES] contiguous frames before it. */
        fun decode(
            frames: List<ChunkFrame>,
            range: IntRange,
            decoder: OpusDecoder,
        ): FloatArray {
            decoder.reset()
            var from = range.first
            while (from > 0 && range.first - from < PREROLL_FRAMES && frames[from - 1].seq + 1 == frames[from].seq) {
                from--
            }
            for (i in from until range.first) decoder.decode(frames[i].payload)
            val parts = range.map { decoder.decode(frames[it].payload) }
            val out = FloatArray(parts.sumOf { it.size })
            var at = 0
            for (part in parts) {
                part.copyInto(out, at)
                at += part.size
            }
            return out
        }
    }
}
