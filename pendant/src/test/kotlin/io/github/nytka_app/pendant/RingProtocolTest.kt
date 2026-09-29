package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.RingFixtures.bytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingProtocolTest {
    @Test
    fun `commands are big endian`() {
        assertArrayEquals(bytes(0x03), RingProtocol.stop())
        assertArrayEquals(bytes(0x10), RingProtocol.info())
        assertArrayEquals(
            bytes(0x11, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            RingProtocol.read(0x0102030405060708L),
        )
        assertArrayEquals(
            bytes(0x11, 0, 0, 0, 0, 0, 0, 0x01, 0x00, 0x00, 0x00, 0x07, 0xD0),
            RingProtocol.read(256, 2_000),
        )
        assertArrayEquals(
            bytes(0x12, 0, 0, 0, 1, 0, 0, 0, 0),
            RingProtocol.advance(0x1_0000_0000L),
        )
    }

    @Test
    fun `a read without a count is nine bytes`() {
        assertEquals(9, RingProtocol.read(5).size)
        assertEquals(9, RingProtocol.read(5, 0).size)
        assertEquals(13, RingProtocol.read(5, 1).size)
    }

    @Test
    fun `the clock is four little endian bytes and reads back`() {
        val written = RingProtocol.clock(1_800_000_000L) // 0x6B49D200
        assertArrayEquals(bytes(0x00, 0xD2, 0x49, 0x6B), written)
        assertEquals(1_800_000_000L, RingProtocol.u32le(written))
        assertEquals(0xFFFFFFFFL, RingProtocol.u32le(bytes(0xFF, 0xFF, 0xFF, 0xFF)))
        assertEquals(64L, RingProtocol.u32le(bytes(0x40, 0, 0, 0)))
        assertNull(RingProtocol.u32le(bytes(1, 2, 3)))
        assertNull(RingProtocol.u32le(null))
    }

    @Test
    fun `the status read is four little endian words`() {
        val read =
            RingProtocol.status(
                bytes(
                    0x00,
                    0x01,
                    0x00,
                    0x00, // used 256
                    0x02,
                    0x00,
                    0x00,
                    0x00, // unread 2
                    0xFF,
                    0xFF,
                    0xFF,
                    0xFF, // free 4294967295
                    0x01,
                    0x00,
                    0x00,
                    0x00, // rtc valid
                ),
            )
        assertEquals(RingStatusRead(256, 2, 0xFFFFFFFFL, true), read)
        assertNull(RingProtocol.status(ByteArray(15)))
    }

    @Test
    fun `ack carries one status byte`() {
        val ack = RingProtocol.parse(bytes(0x01, 10)) as RingNotification.Ack
        assertEquals(10, ack.status)
        assertNull(RingProtocol.parse(bytes(0x01)))
    }

    @Test
    fun `info is 31 big endian bytes`() {
        val value =
            bytes(
                0x02,
                0,
                0,
                0,
                0,
                0,
                0,
                0x01,
                0x02, // readSeq 258
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x05, // writeSeq 4294967301
                0xFF,
                0xFF,
                0xFF,
                0xFE, // capacity 4294967294: no sign
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                7, // dropped
                0x01,
                0xBC, // 444
            )
        val info = (RingProtocol.parse(value) as RingNotification.Info).info
        assertEquals(RingInfo(258, 4_294_967_301L, 4_294_967_294L, 7, 444), info)
        assertNull(RingProtocol.parse(value.copyOf(30)))
    }

    @Test
    fun `read begin is 13 big endian bytes`() {
        val begin =
            RingProtocol.parse(
                bytes(0x05, 0, 0, 0, 0, 0, 0, 0x03, 0xE8, 0x00, 0x00, 0x07, 0xD0),
            ) as RingNotification.ReadBegin
        assertEquals(1_000L, begin.startSeq)
        assertEquals(2_000L, begin.count)
        assertNull(RingProtocol.parse(bytes(0x05, 0, 0, 0, 0, 0, 0, 0, 1)))
    }

    @Test
    fun `done is a status and a big endian next sequence`() {
        val done = RingProtocol.parse(bytes(0x04, 0, 0, 0, 0, 0, 0, 0x27, 0x10, 0x00)) as RingNotification.Done
        assertEquals(0, done.status)
        assertEquals(0x2710_00L, done.nextSeq)
        assertEquals(10, (RingProtocol.parse(RingFixtures.done(10, 5)) as RingNotification.Done).status)
        assertNull(RingProtocol.parse(bytes(0x04, 0, 0, 0)))
    }

    @Test
    fun `data is whatever follows the type byte`() {
        val data = RingProtocol.parse(bytes(0x03, 9, 8, 7)) as RingNotification.Data
        assertArrayEquals(bytes(9, 8, 7), data.bytes)
        assertTrue((RingProtocol.parse(bytes(0x03)) as RingNotification.Data).bytes.isEmpty())
    }

    @Test
    fun `unknown types and empty values parse to nothing`() {
        assertNull(RingProtocol.parse(bytes(0x09, 1, 2, 3)))
        assertNull(RingProtocol.parse(ByteArray(0)))
        assertFalse(RingProtocol.parse(bytes(0x01, 0)) == null)
    }
}
