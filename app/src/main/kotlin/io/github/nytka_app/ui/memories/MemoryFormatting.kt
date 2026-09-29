package io.github.nytka_app.ui.memories

import io.github.nytka_app.ui.conversations.Formatting
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

object MemoryFormatting {
    /** "Today", "Yesterday" or "Monday, 21 September", in the phone's zone. */
    fun day(
        instant: Instant,
        clock: Clock,
    ): String = Formatting.dayTitle(instant.atZone(clock.zone).toLocalDate(), LocalDate.now(clock))
}
