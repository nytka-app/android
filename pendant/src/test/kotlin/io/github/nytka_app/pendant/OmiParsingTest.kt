package io.github.nytka_app.pendant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmiParsingTest {
    @Test
    fun `codec is the first byte`() {
        assertEquals(21, OmiParsing.codec(byteArrayOf(21)))
        assertNull(OmiParsing.codec(byteArrayOf()))
        assertNull(OmiParsing.codec(null))
    }

    @Test
    fun `battery is an unsigned percent`() {
        assertEquals(82, OmiParsing.battery(byteArrayOf(82)))
        assertEquals(200, OmiParsing.battery(byteArrayOf(200.toByte())))
    }

    @Test
    fun `button reads a little endian code from eight bytes`() {
        assertEquals(ButtonEvent.DoubleTap, OmiParsing.button(byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(ButtonEvent.Release, OmiParsing.button(byteArrayOf(5, 0, 0, 0, 9, 9, 9, 9)))
        assertNull(OmiParsing.button(byteArrayOf(2, 0, 0, 1))) // 0x01000002 is no event
        assertNull(OmiParsing.button(byteArrayOf(2)))
    }

    @Test
    fun `text trims the zero padding some firmware sends`() {
        assertEquals("3.0.21", OmiParsing.text("3.0.21\u0000\u0000".toByteArray()))
        assertNull(OmiParsing.text(byteArrayOf()))
    }
}
