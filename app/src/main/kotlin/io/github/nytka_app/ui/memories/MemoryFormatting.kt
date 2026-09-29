package io.github.nytka_app.ui.memories

import io.github.nytka_app.ui.conversations.Formatting
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

object MemoryFormatting {
    /** "Today", "Yesterday" or "Monday, 21 September", in the phone's zone. */
    fun day(
        instant: Instant,
        clock: Clock,
    ): String = Formatting.dayTitle(instant.atZone(clock.zone).toLocalDate(), LocalDate.now(clock))

    /** [timestamp] read with any offset, as [day]; null when it is missing or unreadable. */
    fun day(
        timestamp: String?,
        clock: Clock,
    ): String? = timestamp?.let { runCatching { day(OffsetDateTime.parse(it).toInstant(), clock) }.getOrNull() }
}
