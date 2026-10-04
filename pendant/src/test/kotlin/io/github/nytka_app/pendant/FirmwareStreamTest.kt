package io.github.nytka_app.pendant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareStreamTest {
    @Test
    fun `the consumer pendant maps to its stream`() {
        assertEquals(FirmwareStream.OmiCv1, FirmwareStream.of("Omi CV 1", "3.0.21"))
        assertEquals(FirmwareStream.OmiCv1, FirmwareStream.of(" omi  cv 1 ", "3.0.19"))
    }

    @Test
    fun `any other pendant, or a report that does not match, maps to nothing`() {
        assertNull(FirmwareStream.of("Omi DevKit 2", "2.0.10"))
        assertNull(FirmwareStream.of("Friend DevKit 1", "1.0.5"))
        assertNull(FirmwareStream.of("Omi EVT", "0.0.9"))
        assertNull(FirmwareStream.of("Omi CV 1", "2.0.10"))
        assertNull(FirmwareStream.of("Omi CV 1", "fake"))
        assertNull(FirmwareStream.of("Omi CV 1", null))
        assertNull(FirmwareStream.of(null, "3.0.21"))
        assertNull(FirmwareStream.of("Fake", "fake"))
    }

    @Test
    fun `reads the version of its own tags only`() {
        val stream = FirmwareStream.OmiCv1
        assertEquals(FirmwareVersion(3, 0, 21), stream.versionOf("Omi_CV1_v3.0.21"))
        assertEquals(FirmwareVersion(3, 0, 9), stream.versionOf("Omi_CV1_v3.0.9"))
        assertNull(stream.versionOf("Omi_CV1_v3.0.7_pre"))
        assertNull(stream.versionOf("Omi_DK2_v2.0.10"))
        assertNull(stream.versionOf("v0.12.440+12440-macos"))
        assertNull(stream.versionOf("Omi_CV1_v3.0"))
    }
}
