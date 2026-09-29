package io.github.nytka_app.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Uuid7Test {
    @Test
    fun `carries the time, version 7 and the RFC variant`() {
        val id = Uuid7.next(0x0199_0000_1234L, randomBits = { -1L })

        assertTrue(id.toString().startsWith("01990000-1234-7"))
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
    }

    @Test
    fun `later ids sort after earlier ones`() {
        val ids = listOf(1_000L, 2_000L, 3_000L).map { Uuid7.next(it).toString() }

        assertEquals(ids.sorted(), ids)
    }
}
