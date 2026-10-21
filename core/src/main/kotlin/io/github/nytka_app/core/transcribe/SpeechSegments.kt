package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.chunks.ChunkFrame

/**
 * Finds the stretches of speech in a run of pendant frames without decoding them. The pendant's Opus is
 * variable-bitrate, so a frame's size follows what it carries: about 8 bytes of silence, 50 to 120 of speech (the
 * same reading as `EnrollmentRecording.level`). Silence is never decoded or recognised, which is most of a day.
 */
object SpeechSegments {
    /** A frame larger than this carries sound worth recognising. */
    const val SPEECH_BYTES = 20

    /** This much silence ends a segment: 0.8 s. */
    const val HANG_FRAMES = 40

    /** Kept on each side of the speech, so the first and last syllables are whole: 0.2 s. */
    const val PAD_FRAMES = 10

    /** Less speech than this is a cough or a door: 0.3 s. */
    const val MIN_SPEECH_FRAMES = 15

    /** The longest segment handed to the recogniser: 30 s, which keeps its memory flat. */
    const val MAX_FRAMES = 1500

    /** Index ranges into [frames], in order. A gap in sequence numbers always ends a segment. */
    fun find(frames: List<ChunkFrame>): List<IntRange> {
        val segments = mutableListOf<IntRange>()
        var start = -1
        var lastSpeech = -1
        var speech = 0

        fun close() {
            if (start >= 0 && speech >= MIN_SPEECH_FRAMES) {
                val from = pad(frames, start, -1)
                val to = pad(frames, lastSpeech, 1)
                segments += split(frames, from..to)
            }
            start = -1
            speech = 0
        }
        for (i in frames.indices) {
            if (start >= 0 && i > 0 && frames[i].seq != frames[i - 1].seq + 1) close()
            if (frames[i].payload.size > SPEECH_BYTES) {
                if (start < 0) start = i
                lastSpeech = i
                speech++
            } else if (start >= 0 && i - lastSpeech >= HANG_FRAMES) {
                close()
            }
        }
        close()
        return segments
    }

    /** Cuts a range longer than [MAX_FRAMES] at its quietest frame in the last third of each piece. */
    private fun split(
        frames: List<ChunkFrame>,
        range: IntRange,
    ): List<IntRange> {
        val pieces = mutableListOf<IntRange>()
        var from = range.first
        while (range.last - from + 1 > MAX_FRAMES) {
            val window = (from + MAX_FRAMES * 2 / 3) until (from + MAX_FRAMES)
            val cut = window.minBy { frames[it].payload.size }
            pieces += from..cut
            from = cut + 1
        }
        pieces += from..range.last
        return pieces
    }

    /** Up to [PAD_FRAMES] steps from [index] in [direction], never across a gap. */
    private fun pad(
        frames: List<ChunkFrame>,
        index: Int,
        direction: Int,
    ): Int {
        var i = index
        repeat(PAD_FRAMES) {
            val next = i + direction
            if (next !in frames.indices || frames[maxOf(i, next)].seq != frames[minOf(i, next)].seq + 1) return i
            i = next
        }
        return i
    }
}
