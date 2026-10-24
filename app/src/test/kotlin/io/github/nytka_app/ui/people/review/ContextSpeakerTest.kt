package io.github.nytka_app.ui.people.review

import io.github.nytka_app.core.api.ReviewContextLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextSpeakerTest {
    @Test
    fun `the wearer, a named person, voices numbered as the server numbers them and a line without a speaker`() {
        val lines =
            listOf(
                ReviewContextLine(speaker = "SPEAKER_4"),
                ReviewContextLine(speaker = "SPEAKER_1", isUser = true),
                ReviewContextLine(speaker = "SPEAKER_7", personName = "Olena"),
                ReviewContextLine(speaker = "SPEAKER_2"),
                ReviewContextLine(speaker = "SPEAKER_4"),
                ReviewContextLine(),
            )

        assertEquals(
            listOf(
                ContextSpeaker.Voice(5),
                ContextSpeaker.You,
                ContextSpeaker.Person("Olena"),
                ContextSpeaker.Voice(3),
                ContextSpeaker.Voice(5),
                ContextSpeaker.Someone,
            ),
            ContextSpeaker.of(lines),
        )
    }
}
