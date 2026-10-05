package io.github.nytka_app.ui.people

import io.github.nytka_app.ui.conversations.Formatting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** When someone was last heard, as far as a row words it: "Today 14:03", "Yesterday", "3 Oct". */
sealed interface LastSeen {
    data class Today(
        val time: LocalTime,
    ) : LastSeen

    data object Yesterday : LastSeen

    data class On(
        val date: LocalDate,
    ) : LastSeen

    companion object {
        /** Null when [text] is absent or not a time. */
        fun of(
            text: String?,
            today: LocalDate,
            zone: ZoneId,
        ): LastSeen? {
            val instant: Instant = text?.let(Formatting::parse) ?: return null
            val local = instant.atZone(zone)
            return when (local.toLocalDate()) {
                today -> Today(local.toLocalTime())
                today.minusDays(1) -> Yesterday
                else -> On(local.toLocalDate())
            }
        }
    }
}
