package io.github.nytka_app.ui.conversations

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The interface is in English (vision: "The interface is in English"); times follow the phone's zone. */
object Formatting {
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** Reads an ISO 8601 time with `Z` or an offset such as `+00:00`; an unreadable one counts as the epoch. */
    fun instant(text: String): Instant = parse(text) ?: Instant.EPOCH

    fun parse(text: String): Instant? = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    fun dayTitle(
        date: LocalDate,
        today: LocalDate,
    ): String =
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(date)
        }

    fun clock(
        instant: Instant,
        zone: ZoneId,
    ): String = clockFormat.format(instant.atZone(zone))

    fun timeRange(
        start: Instant,
        end: Instant,
        zone: ZoneId,
    ) = "${clock(start, zone)}–${clock(end, zone)}"

    fun length(
        start: Instant,
        end: Instant,
    ): String {
        val seconds = Duration.between(start, end).seconds
        if (seconds < 60) return "under a minute"
        val minutes = (seconds + 30) / 60
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    /** A playback position: 1:05, or 1:02:05 from an hour on. */
    fun position(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val (h, m, sec) = Triple(seconds / 3600, seconds / 60 % 60, seconds % 60)
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
