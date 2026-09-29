package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.RingFixtures.bytes
import io.github.nytka_app.pendant.RingFixtures.frame
import io.github.nytka_app.pendant.RingFixtures.record
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingRecordsTest {
    @Test
    fun `the stamp is a big endian u32 with no sign`() {
        val raw = ByteArray(444)
        bytes(0x6B, 0x49, 0xD2, 0x00).copyInto(raw)
        assertEquals(1_800_000_000L, RingRecords.parse(7, raw).stampS)
        bytes(0xFF, 0xFF, 0xFF, 0xFF).copyInto(raw)
        assertEquals(4_294_967_295L, RingRecords.parse(7, raw).stampS)
        assertEquals(7L, RingRecords.parse(7, raw).seq)
    }

    @Test
    fun `frames are length prefixed and zero bytes are padding`() {
        val raw = record(1_800_000_000L, listOf(frame(97, 1), frame(96, 2)))
        raw[4 + 97 + 1 + 96 + 1] = 0 // padding before the end
        val parsed = RingRecords.parse(0, raw)
        assertEquals(listOf(97, 96), parsed.frames.map { it.size })
        assertArrayEquals(frame(97, 1), parsed.frames[0])
        assertArrayEquals(frame(96, 2), parsed.frames[1])
    }

    @Test
    fun `a record of four frames like the live stream`() {
        val frames = List(4) { frame(97, it + 1) }
        val parsed = RingRecords.parse(0, record(1_800_000_000L, frames))
        assertEquals(4, parsed.frames.size)
        assertEquals(listOf<Byte>(1, 2, 3, 4), parsed.frames.map { it[0] })
    }

    @Test
    fun `a frame ending on the last but one byte still counts`() {
        // 4 x 100 + 34 = 439 audio bytes: the firmware's most.
        val frames = List(4) { frame(99, 5) } + frame(38, 6)
        val raw = record(1L, frames)
        val parsed = RingRecords.parse(0, raw)
        assertEquals(5, parsed.frames.size)
        assertEquals(38, parsed.frames.last().size)
    }

    @Test
    fun `a frame that would reach byte 440 is not read`() {
        // offset 400 into the audio: 1 + 39 = 440, the boundary the official app breaks on with >=.
        val raw = record(1L, List(4) { frame(99, 5) }, overflowLength = 39)
        val parsed = RingRecords.parse(0, raw)
        assertEquals(4, parsed.frames.size)
    }

    @Test
    fun `stale bytes behind an overflow length byte are never read`() {
        // The tail holds an earlier block: 0x20 lengths that would parse as frames if the reader went on.
        val frames = List(4) { frame(99, 5) }
        val raw = record(1L, frames, tail = 0x20, overflowLength = 60)
        val parsed = RingRecords.parse(0, raw)
        assertEquals(4, parsed.frames.size)
    }

    @Test
    fun `an empty record has no frames`() {
        assertTrue(RingRecords.parse(0, ByteArray(444)).frames.isEmpty())
    }

    @Test
    fun `a record must be 444 bytes`() {
        val failed = runCatching { RingRecords.parse(0, ByteArray(443)) }
        assertTrue(failed.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `the reassembler joins unaligned chunks into numbered records`() {
        val a = record(10, listOf(frame(50, 1)))
        val b = record(11, listOf(frame(60, 2)))
        val c = record(12, listOf(frame(70, 3)))
        val stream = a + b + c
        val reassembler = RingRecordReassembler()
        reassembler.restart(500)
        val got = mutableListOf<RingRecord>()
        // Chunks of 240 bytes, the size a 247 MTU gives, do not line up with 444.
        for (start in stream.indices step 240) {
            got += reassembler.append(stream.copyOfRange(start, minOf(start + 240, stream.size)))
        }
        assertEquals(listOf(500L, 501L, 502L), got.map { it.seq })
        assertEquals(listOf(10L, 11L, 12L), got.map { it.stampS })
        assertEquals(listOf(50, 60, 70), got.map { it.frames.single().size })
        assertEquals(0, reassembler.pendingBytes)
    }

    @Test
    fun `a record only completes on its last byte`() {
        val reassembler = RingRecordReassembler()
        reassembler.restart(0)
        val raw = record(10, listOf(frame(50, 1)))
        assertTrue(reassembler.append(raw.copyOf(443)).isEmpty())
        assertEquals(443, reassembler.pendingBytes)
        assertEquals(1, reassembler.append(raw.copyOfRange(443, 444)).size)
    }

    @Test
    fun `restart drops the partial record and renumbers`() {
        val reassembler = RingRecordReassembler()
        reassembler.restart(0)
        reassembler.append(ByteArray(300) { 7 })
        reassembler.restart(90)
        assertEquals(0, reassembler.pendingBytes)
        val got = reassembler.append(record(10, listOf(frame(50, 1))))
        assertEquals(90L, got.single().seq)
        assertEquals(10L, got.single().stampS)
    }
}
