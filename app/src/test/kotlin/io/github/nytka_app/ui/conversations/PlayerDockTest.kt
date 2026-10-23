package io.github.nytka_app.ui.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerDockTest {
    private val starts = listOf(0L, 4_000L, 9_000L, 12_000L)

    private fun paragraph(speaker: String?) = Paragraph("08:00", "Line.", speaker)

    @Test
    fun `the heard paragraph is the last that has started`() {
        assertEquals(0, playingParagraph(starts, 0))
        assertEquals(1, playingParagraph(starts, 8_999))
        assertEquals(2, playingParagraph(starts, 9_000))
        assertEquals(3, playingParagraph(starts, 99_000))
    }

    @Test
    fun `no position map means no heard paragraph`() {
        assertNull(playingParagraph(emptyList(), 5_000))
    }

    @Test
    fun `ticks sit where the speaker changes, not between lines of one speaker`() {
        val paragraphs = listOf(paragraph("Ann"), paragraph("Ann"), paragraph("Bo"), paragraph("Ann"))

        assertEquals(listOf(0.45f, 0.6f), turnPositions(paragraphs, starts, 20_000))
    }

    @Test
    fun `no ticks without a length`() {
        assertEquals(emptyList<Float>(), turnPositions(listOf(paragraph("Ann"), paragraph("Bo")), starts, 0))
    }

    @Test
    fun `the dock numbers unnamed voices as the transcript does, never by the raw label`() {
        val paragraphs =
            listOf(
                Paragraph("08:00", "Hi.", "SPEAKER_1"),
                Paragraph("08:01", "Me.", "SPEAKER_0", isUser = true),
                Paragraph("08:02", "Yes.", "Olena", personId = "p1"),
                Paragraph("08:03", "No.", "SPEAKER_3"),
            )
        val voices = paragraphs.voiceNumbers()

        assertEquals(2, voices.of("SPEAKER_1"))
        assertEquals(4, voices.of("SPEAKER_3"))
    }
}
