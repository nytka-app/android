package io.github.nytka_app.pendant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareVersionTest {
    @Test
    fun `parses the revision the pendant reports`() {
        assertEquals(FirmwareVersion(3, 0, 21), FirmwareVersion.parse("3.0.21"))
        assertEquals(FirmwareVersion(3, 0, 19), FirmwareVersion.parse(" v3.0.19-rc1 "))
    }

    @Test
    fun `rejects what is no revision`() {
        assertNull(FirmwareVersion.parse(null))
        assertNull(FirmwareVersion.parse(""))
        assertNull(FirmwareVersion.parse("fake"))
        assertNull(FirmwareVersion.parse("3.0"))
    }

    @Test
    fun `compares numerically not as text`() {
        assertTrue(FirmwareVersion(3, 0, 9) < FirmwareVersion.RING_STORAGE)
        assertTrue(FirmwareVersion(3, 0, 19) < FirmwareVersion.RING_STORAGE)
        assertTrue(FirmwareVersion(3, 0, 20) >= FirmwareVersion.RING_STORAGE)
        assertTrue(FirmwareVersion(3, 1, 0) > FirmwareVersion.RING_STORAGE)
        assertTrue(FirmwareVersion(10, 0, 0) > FirmwareVersion.RING_STORAGE)
    }
}
