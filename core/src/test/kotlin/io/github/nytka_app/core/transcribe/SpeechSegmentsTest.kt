package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.chunks.ChunkFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmentsTest {
    /** [pattern]: one character per frame, `s` speech (60 bytes), `.` silence (8 bytes). */
    private fun frames(
        pattern: String,
        firstSeq: Long = 0,
    ) = pattern.mapIndexed { i, c -> ChunkFrame(firstSeq + i, (firstSeq + i) * 20, ByteArray(if (c == 's') 60 else 8)) }

    @Test
    fun `silence alone makes no segment`() {
        assertTrue(SpeechSegments.find(frames(".".repeat(500))).isEmpty())
    }

    @Test
    fun `speech is padded on both sides`() {
        val segments = SpeechSegments.find(frames(".".repeat(50) + "s".repeat(30) + ".".repeat(100)))

        assertEquals(listOf(40..89), segments)
    }

    @Test
    fun `a short pause stays inside one segment and a long one splits it`() {
        val short = SpeechSegments.find(frames("s".repeat(20) + ".".repeat(30) + "s".repeat(20)))
        val long = SpeechSegments.find(frames("s".repeat(20) + ".".repeat(60) + "s".repeat(20)))

        assertEquals(1, short.size)
        assertEquals(2, long.size)
    }

    @Test
    fun `a click shorter than the minimum is dropped`() {
        assertTrue(SpeechSegments.find(frames(".".repeat(50) + "s".repeat(5) + ".".repeat(50))).isEmpty())
    }

    @Test
    fun `a gap in sequence numbers ends a segment and padding never crosses it`() {
        val list = frames("s".repeat(20)) + frames("s".repeat(20), firstSeq = 100)

        assertEquals(listOf(0..19, 20..39), SpeechSegments.find(list))
    }

    @Test
    fun `long speech is cut into pieces no longer than the maximum`() {
        val segments = SpeechSegments.find(frames("s".repeat(4000)))

        assertTrue(segments.all { it.count() <= SpeechSegments.MAX_FRAMES })
        assertEquals(4000, segments.sumOf { it.count() })
        assertEquals(0, segments.first().first)
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
    }
}
