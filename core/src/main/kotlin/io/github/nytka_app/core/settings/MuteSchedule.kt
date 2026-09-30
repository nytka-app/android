package io.github.nytka_app.core.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One weekly window: from [start] on each of [days] until [end], the phone's wall clock. An [end] at or before
 * [start] runs past midnight into the next day. [start] counts as inside, [end] as outside. A window with no days,
 * or with [start] equal to [end], mutes nothing.
 */
data class MuteWindow(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
) {
    private val crossesMidnight get() = end < start

    val active: Boolean get() = days.isNotEmpty() && start != end

    fun contains(at: ZonedDateTime): Boolean {
        if (!active) return false
        val time = at.toLocalTime()
        return if (crossesMidnight) {
            (at.dayOfWeek in days && time >= start) || (at.dayOfWeek.minus(1) in days && time < end)
        } else {
            at.dayOfWeek in days && time >= start && time < end
        }
    }

    /** Every start and end that falls within a day of [from]'s date, back one day and forward a week. */
    internal fun boundaries(from: ZonedDateTime): List<ZonedDateTime> {
        if (!active) return emptyList()
        val today = from.toLocalDate()
        return (-1L..DAYS_AHEAD).flatMap { offset ->
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) return@flatMap emptyList()
            val endDate = if (crossesMidnight) date.plusDays(1) else date
            listOf(
                ZonedDateTime.of(date, start, from.zone),
                ZonedDateTime.of(endDate, end, from.zone),
            )
        }
    }

    private companion object {
        const val DAYS_AHEAD = 7L
    }
}

/**
 * The weekly windows in which the pendant's audio stays off, like a manual mute. Times are the phone's wall clock,
 * so a change of time zone moves the windows with it.
 */
data class MuteSchedule(
    val windows: List<MuteWindow> = emptyList(),
) {
    fun mutedAt(at: ZonedDateTime): Boolean = windows.any { it.contains(at) }

    /** How long until [mutedAt] can change; null when no window is active. */
    fun untilChange(at: ZonedDateTime): Duration? =
        windows
            .flatMap { it.boundaries(at) }
            .filter { it > at }
            .minOrNull()
            ?.let { Duration.between(at, it) }

    /**
     * The server's `mute.windows` value: JSON with ISO weekdays (1 Monday to 7 Sunday) and local `HH:mm`. Windows that
     * mute nothing are left out, so an empty schedule is `[]`.
     */
    fun toServerJson(): String =
        JsonArray(
            windows.filter { it.active }.map { w ->
                JsonObject(
                    mapOf(
                        "days" to
                            JsonArray(
                                w.days
                                    .map { it.value }
                                    .sorted()
                                    .map(::JsonPrimitive),
                            ),
                        "start" to JsonPrimitive(w.start.format(SERVER_TIME)),
                        "end" to JsonPrimitive(w.end.format(SERVER_TIME)),
                    ),
                )
            },
        ).toString()

    /** For DataStore: `days,startMinute,endMinute` per window, joined by `;`; days is a Monday-first bitmask. */
    fun encode(): String =
        windows.joinToString(";") { w ->
            val mask = w.days.fold(0) { bits, day -> bits or (1 shl (day.value - 1)) }
            "$mask,${w.start.toSecondOfDay() / SECONDS_PER_MINUTE},${w.end.toSecondOfDay() / SECONDS_PER_MINUTE}"
        }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private val SERVER_TIME = DateTimeFormatter.ofPattern("HH:mm")
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        private const val DAY_MASK = 0x7F

        /** Whether the server's stored `mute.windows` says the same as [json], whatever its spacing; null is `[]`. */
        fun sameOnServer(
            stored: String?,
            json: String,
        ): Boolean {
            fun parse(text: String?): JsonElement? = runCatching { Json.parseToJsonElement(text ?: "[]") }.getOrNull()
            val left = parse(stored)
            return left != null && left == parse(json)
        }

        /** What [encode] wrote; a window that does not parse is skipped, so a damaged value never blocks capture. */
        fun decode(text: String?): MuteSchedule = MuteSchedule(text.orEmpty().split(';').mapNotNull(::decodeWindow))

        private fun decodeWindow(part: String): MuteWindow? {
            val fields = part.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (fields.size != 3) return null
            val (mask, start, end) = fields
            if (start !in 0 until MINUTES_PER_DAY || end !in 0 until MINUTES_PER_DAY) return null
            return MuteWindow(
                days = DayOfWeek.entries.filter { mask and DAY_MASK and (1 shl (it.value - 1)) != 0 }.toSet(),
                start = LocalTime.of(start / MINUTES_PER_HOUR, start % MINUTES_PER_HOUR),
                end = LocalTime.of(end / MINUTES_PER_HOUR, end % MINUTES_PER_HOUR),
            )
        }
    }
}
