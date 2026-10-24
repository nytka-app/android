package io.github.nytka_app.ui.names

/**
 * The number each voice nobody has named shows as "Voice n", by the server's rule (`SpeakerLabel.Voice`): the
 * provider's `SPEAKER_n` is Voice n+1, the name summaries, Ask, MCP and exports use, so "Voice 2" on screen and in a
 * summary is the same voice. A label already shown as "Voice n" keeps n. Any other label takes the next number after
 * those, by first appearance in [labels].
 */
class VoiceNumbers(
    labels: List<String>,
) {
    private val numbers: Map<String, Int>

    init {
        val fixed = labels.distinct().mapNotNull { label -> serverNumber(label)?.let { label to it } }.toMap()
        var next = (fixed.values.maxOrNull() ?: 0) + 1
        numbers = fixed + labels.distinct().filter { it !in fixed }.associateWith { next++ }
    }

    /** The number [label] shows as; a label not in the list gets the server's number, or 0 when it has none. */
    fun of(label: String): Int = numbers[label] ?: serverNumber(label) ?: 0

    companion object {
        private val provider = Regex("^SPEAKER_([0-9]{1,6})$")
        private val shown = Regex("^Voice ([0-9]{1,7})$")

        /** The number the server gives [label], or null for a label it shows as stored. */
        fun serverNumber(label: String): Int? =
            provider.find(label)?.let { it.groupValues[1].toInt() + 1 }
                ?: shown.find(label)?.let { it.groupValues[1].toInt() }
    }
}
