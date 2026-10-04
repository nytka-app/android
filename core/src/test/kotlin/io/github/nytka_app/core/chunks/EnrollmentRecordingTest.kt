package io.github.nytka_app.core.chunks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EnrollmentRecordingTest {
    private val session = UUID.fromString("0192f0c4-1b2a-7c3d-8e4f-5a6b7c8d9e0f")

    private fun recording(
        frames: Int,
        size: Int = 80,
    ) = EnrollmentRecording(session).apply {
        repeat(frames) { add(ByteArray(size) { i -> (it + i).toByte() }, 1_000L + it * 20L) }
    }

    @Test
    fun `frames become chunks of at most 30 seconds back to back, numbered without holes`() {
        val chunks = ChunkReader.readAll(recording(3_200).body())

        assertEquals(listOf(1_500, 1_500, 200), chunks.map { it.frames.size })
        assertEquals(listOf(0L, 1_500L, 3_000L), chunks.map { it.firstSeq })
        assertEquals(setOf(session), chunks.map { it.session }.toSet())
        assertEquals((0L until 3_200L).toList(), chunks.flatMap { c -> c.frames.map { it.seq } })
        assertEquals(1_000L + 1_500 * 20L, chunks[1].baseTimeMs)
        assertEquals(1_000L + 3_199 * 20L, chunks[2].frames.last().capturedAtMs)
    }

    @Test
    fun `a clock that steps back keeps every offset positive`() {
        val reading = EnrollmentRecording(session)
        reading.add(byteArrayOf(1), 5_000)
        reading.add(byteArrayOf(2), 4_000)

        val chunk = ChunkReader.readAll(reading.body()).single()

        assertEquals(4_000L, chunk.baseTimeMs)
        assertEquals(listOf(5_000L, 4_000L), chunk.frames.map { it.capturedAtMs })
    }

    @Test
    fun `the cap is 120 seconds and the frame past it is refused`() {
        val reading = recording(EnrollmentRecording.MAX_FRAMES - 1)
        assertFalse(reading.full)

        assertTrue(reading.add(byteArrayOf(1), 0))
        assertTrue(reading.full)
        assertFalse(reading.add(byteArrayOf(1), 0))

        assertEquals(6_000, reading.frameCount)
        assertEquals(120.0, reading.seconds, 0.0)
        assertEquals(6_000, ChunkReader.readAll(reading.body()).sumOf { it.frames.size })
    }

    @Test
    fun `nothing recorded is an empty body`() {
        assertEquals(0, EnrollmentRecording().body().size)
    }

    @Test
    fun `the level follows the size of the last frames`() {
        assertEquals(0f, recording(0).level(), 0f)
        assertEquals(0f, recording(20, size = 8).level(), 0f)
        assertEquals(1f, recording(20, size = 120).level(), 0f)
        val speech = recording(20, size = 56).level()
        assertTrue(speech in 0.4f..0.6f)
    }
}
