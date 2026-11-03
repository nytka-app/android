package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessFixtures.opus
import io.github.nytka_app.pendant.LimitlessWire.intField
import io.github.nytka_app.pendant.LimitlessWire.message
import io.github.nytka_app.pendant.RingFixtures.bytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitlessProtocolTest {
    @Test
    fun `varints round trip`() {
        for (value in listOf(0L, 1L, 127L, 128L, 300L, 1_800_000_000_000L)) {
            val encoded = LimitlessWire.varint(value)
            assertEquals(value, LimitlessWire.readVarint(encoded, 0).first)
            assertEquals(encoded.size, LimitlessWire.readVarint(encoded, 0).second)
        }
    }

    @Test
    fun `message 8 for download mode is the bytes the protocol buffer layout gives`() {
        // 08 00 10 00 18 01: index 0, seq 0, one fragment; 22 0d: payload of 13 bytes;
        // 42 04 08 01 10 00: message 8 {batch 1, realTime 0}; f2 01 04 08 01 10 00: request data 30 {id 1, 0}.
        val expected =
            bytes(
                0x08,
                0x00,
                0x10,
                0x00,
                0x18,
                0x01,
                0x22,
                0x0d,
                0x42,
                0x04,
                0x08,
                0x01,
                0x10,
                0x00,
                0xf2,
                0x01,
                0x04,
                0x08,
                0x01,
                0x10,
                0x00,
            )
        assertArrayEquals(expected, LimitlessProtocol.setMode(0, 1, LimitlessMode.Download))
    }

    @Test
    fun `commands carry their message number and fields`() {
        val commands = LimitlessCommands()
        assertEquals(
            6 to mapOf(1 to 1_800_000_000_000L),
            LimitlessMessages.command(commands.setCurrentTime(1_800_000_000_000L)),
        )
        assertEquals(8 to mapOf(1 to 1L, 2 to 0L), LimitlessMessages.command(commands.setMode(LimitlessMode.Download)))
        assertEquals(8 to mapOf(1 to 0L, 2 to 1L), LimitlessMessages.command(commands.setMode(LimitlessMode.Live)))
        assertEquals(
            8 to mapOf(1 to 0L, 2 to 0L),
            LimitlessMessages.command(commands.setMode(LimitlessMode.RecordOnly)),
        )
        assertEquals(7 to mapOf(1 to 41L), LimitlessMessages.command(commands.acknowledge(41)))
        assertEquals(21 to emptyMap<Int, Long>(), LimitlessMessages.command(commands.getStatus()))
        assertEquals(26 to mapOf(1 to 100L), LimitlessMessages.command(commands.setLed(150)))
        assertEquals(26 to mapOf(1 to 0L), LimitlessMessages.command(commands.setLed(-5)))
    }

    @Test
    fun `each command gets the next message index and request id`() {
        val commands = LimitlessCommands()
        val first = LimitlessProtocol.parsePacket(commands.getStatus())!!
        val second = LimitlessProtocol.parsePacket(commands.getStatus())!!
        assertEquals(0, first.index)
        assertEquals(1, second.index)
        assertEquals(1, second.numFrags)
    }

    @Test
    fun `a notification that is not a wrapper is not a packet`() {
        assertNull(LimitlessProtocol.parsePacket(ByteArray(0)))
        assertNull(LimitlessProtocol.parsePacket(bytes(0x08, 0x01)))
        assertNull(LimitlessProtocol.parsePacket(bytes(0x08, 0x01, 0x18, 0x01, 0x22, 0x09, 0x01)))
    }

    @Test
    fun `fragments join in order whatever order they arrive in`() {
        val payload = ByteArray(100) { it.toByte() }
        val parts = LimitlessMessages.notifications(3, payload, 30)
        assertEquals(4, parts.size)
        val reassembler = LimitlessReassembler()
        val packets = listOf(parts[2], parts[0], parts[3], parts[1]).map { LimitlessProtocol.parsePacket(it)!! }
        assertNull(reassembler.add(packets[0]))
        assertNull(reassembler.add(packets[1]))
        assertNull(reassembler.add(packets[2]))
        assertArrayEquals(payload, reassembler.add(packets[3]))
    }

    @Test
    fun `reset drops the fragments of a message that never finished`() {
        val parts = LimitlessMessages.notifications(3, ByteArray(100), 30).map { LimitlessProtocol.parsePacket(it)!! }
        val reassembler = LimitlessReassembler()
        reassembler.add(parts[0])
        reassembler.reset()
        reassembler.add(parts[1])
        reassembler.add(parts[2])
        assertNull(reassembler.add(parts[3]))
    }

    @Test
    fun `a message that never finishes is forgotten after the limit`() {
        val reassembler = LimitlessReassembler(limit = 2)
        val stuck = LimitlessMessages.notifications(1, ByteArray(100), 30).map { LimitlessProtocol.parsePacket(it)!! }
        reassembler.add(stuck[0])
        for (index in 2..3) {
            reassembler.add(
                LimitlessProtocol.parsePacket(LimitlessMessages.notifications(index, ByteArray(100), 30)[0])!!,
            )
        }
        for (part in stuck.drop(1)) assertNull(reassembler.add(part))
    }

    @Test
    fun `pages round trip with their index, clock and frames`() {
        val first = LimitlessMessages.page(1_800_000_000_123L, listOf(opus(fill = 1), opus(fill = 2)))
        val second = LimitlessMessages.page(1_800_000_001_000L, listOf(opus(fill = 3)))
        val pages = LimitlessPages.parsePages(LimitlessMessages.pages(7, listOf(41L to first, 42L to second)))
        assertEquals(2, pages.size)
        assertEquals(41L, pages[0]!!.index)
        assertEquals(1_800_000_000_123L, pages[0]!!.timestampMs)
        assertEquals(2, pages[0]!!.frames.size)
        assertArrayEquals(opus(fill = 2), pages[0]!!.frames[1])
        assertEquals(42L, pages[1]!!.index)
        assertArrayEquals(opus(fill = 3), pages[1]!!.frames.single())
    }

    @Test
    fun `a page with audio that cannot be read is null, so that nothing frees it`() {
        val unreadable = LimitlessMessages.page(1_800_000_000_000L, listOf(ByteArray(12)))
        val pages = LimitlessPages.parsePages(LimitlessMessages.pages(1, listOf(5L to unreadable)))
        assertEquals(listOf(null), pages)
    }

    @Test
    fun `a page without audio is a page with no frames`() {
        val bare = LimitlessMessages.page(1_800_000_000_000L, emptyList())
        val pages = LimitlessPages.parsePages(LimitlessMessages.pages(1, listOf(5L to bare)))
        assertEquals(5L, pages.single()!!.index)
        assertTrue(pages.single()!!.frames.isEmpty())
    }

    @Test
    fun `frames come out of a live message`() {
        val frames = listOf(opus(fill = 1), opus(fill = 2), opus(fill = 3))
        val extracted = LimitlessPages.extractFrames(LimitlessMessages.live(frames))
        assertEquals(3, extracted.size)
        assertArrayEquals(frames[2], extracted[2])
    }

    @Test
    fun `only the known table-of-contents bytes make an Opus packet`() {
        for (toc in listOf(0xb8, 0x78, 0xf8, 0xb0, 0x70, 0xf0)) assertTrue(LimitlessPages.isOpusToc(toc.toByte()))
        assertFalse(LimitlessPages.isOpusToc(0x00))
        assertFalse(LimitlessPages.isOpusToc(0xb9.toByte()))
    }

    @Test
    fun `the flash status reads back from a notification`() {
        val flash = LimitlessFlash(oldest = 120, newest = 4_000, session = 9, free = 600, total = 1_000_000)
        val notification = LimitlessMessages.notifications(2, LimitlessMessages.flash(flash), 200).single()
        assertEquals(flash, LimitlessStatus.parseFlash(notification))
    }

    @Test
    fun `something that is not a status is not read as one`() {
        assertNull(LimitlessStatus.parseFlash(ByteArray(0)))
        assertNull(LimitlessStatus.parseFlash(ByteArray(40)))
        assertNull(
            LimitlessStatus.parseFlash(
                LimitlessMessages.notifications(1, LimitlessMessages.live(listOf(opus())), 200).single(),
            ),
        )
    }

    @Test
    fun `a double press is a button and other events are not`() {
        val double = LimitlessMessages.notifications(4, LimitlessMessages.doublePress(), 200).single()
        assertEquals(ButtonEvent.DoubleTap, LimitlessStatus.parseButton(double))
        val single = LimitlessMessages.notifications(4, message(8, intField(1, 1)), 200).single()
        assertNull(LimitlessStatus.parseButton(single))
        assertNull(LimitlessStatus.parseButton(ByteArray(0)))
        assertNotNull(LimitlessStatus.parseButton(double))
    }
}
