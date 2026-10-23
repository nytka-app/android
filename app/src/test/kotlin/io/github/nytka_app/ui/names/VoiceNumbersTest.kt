package io.github.nytka_app.ui.names

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNumbersTest {
    @Test
    fun `a provider label is numbered as the server numbers it, whatever the order on screen`() {
        val voices = VoiceNumbers(listOf("SPEAKER_2", "SPEAKER_0", "SPEAKER_2"))

        assertEquals(3, voices.of("SPEAKER_2"))
        assertEquals(1, voices.of("SPEAKER_0"))
    }

    @Test
    fun `a label already shown as Voice n keeps its number`() {
        assertEquals(4, VoiceNumbers(listOf("Voice 4")).of("Voice 4"))
    }

    @Test
    fun `other labels follow the server's numbers so none collides`() {
        val voices = VoiceNumbers(listOf("guest", "SPEAKER_1", "host", "guest"))

        assertEquals(2, voices.of("SPEAKER_1"))
        assertEquals(3, voices.of("guest"))
        assertEquals(4, voices.of("host"))
    }

    @Test
    fun `labels the server shows as stored have no number of their own`() {
        assertNull(VoiceNumbers.serverNumber("SPEAKER_x"))
        assertNull(VoiceNumbers.serverNumber("Olena"))
    }
}
