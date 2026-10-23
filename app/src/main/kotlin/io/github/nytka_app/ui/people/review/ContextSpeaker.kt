package io.github.nytka_app.ui.people.review

import io.github.nytka_app.core.api.ReviewContextLine
import io.github.nytka_app.ui.names.VoiceNumbers

/** Who said a context line, as the card words it; the server's own label ("SPEAKER_4") is never shown. */
sealed interface ContextSpeaker {
    data object You : ContextSpeaker

    data class Person(
        val name: String,
    ) : ContextSpeaker

    /** A voice nobody has named, numbered as the transcript and the server number it ([VoiceNumbers]). */
    data class Voice(
        val number: Int,
    ) : ContextSpeaker

    data object Someone : ContextSpeaker

    companion object {
        fun of(lines: List<ReviewContextLine>): List<ContextSpeaker> {
            val voices =
                VoiceNumbers(
                    lines.filter { !it.isUser && it.personName.isNullOrBlank() }.mapNotNull {
                        it.speaker?.takeIf(
                            String::isNotBlank,
                        )
                    },
                )
            return lines.map { line ->
                val label = line.speaker?.takeIf(String::isNotBlank)
                val name = line.personName?.takeIf(String::isNotBlank)
                when {
                    line.isUser -> You
                    name != null -> Person(name)
                    label != null -> Voice(voices.of(label))

                    else -> Someone
                }
            }
        }
    }
}
