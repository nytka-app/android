package io.github.nytka_app.ui.people.review

import io.github.nytka_app.core.api.ReviewContextLine

/** Who said a context line, as the card words it; the server's own label ("SPEAKER_4") is never shown. */
sealed interface ContextSpeaker {
    data object You : ContextSpeaker

    data class Person(
        val name: String,
    ) : ContextSpeaker

    /** A voice nobody has named, numbered by first appearance on the card, as the transcript does. */
    data class Voice(
        val number: Int,
    ) : ContextSpeaker

    data object Someone : ContextSpeaker

    companion object {
        fun of(lines: List<ReviewContextLine>): List<ContextSpeaker> {
            val voices = mutableListOf<String>()
            return lines.map { line ->
                val label = line.speaker?.takeIf(String::isNotBlank)
                val name = line.personName?.takeIf(String::isNotBlank)
                when {
                    line.isUser -> You
                    name != null -> Person(name)
                    label != null -> {
                        if (label !in voices) voices += label
                        Voice(voices.indexOf(label) + 1)
                    }

                    else -> Someone
                }
            }
        }
    }
}
