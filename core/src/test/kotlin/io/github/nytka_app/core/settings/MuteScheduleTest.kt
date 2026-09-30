package io.github.nytka_app.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MuteScheduleTest {
    private val utc = ZoneId.of("UTC")

    // 2026-10-01 is a Thursday.
    private fun at(
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = utc,
    ) = ZonedDateTime.of(LocalDateTime.of(2026, 10, day, hour, minute), zone)

    private fun window(
        vararg days: DayOfWeek,
        from: String,
        to: String,
    ) = MuteWindow(days.toSet(), java.time.LocalTime.parse(from), java.time.LocalTime.parse(to))

    @Test
    fun `a daytime window includes its start and excludes its end`() {
        val schedule = MuteSchedule(listOf(window(DayOfWeek.THURSDAY, from = "09:00", to = "10:00")))

        assertFalse(schedule.mutedAt(at(1, 8, 59)))
        assertTrue(schedule.mutedAt(at(1, 9, 0)))
        assertTrue(schedule.mutedAt(at(1, 9, 59)))
        assertFalse(schedule.mutedAt(at(1, 10, 0)))
        assertFalse(schedule.mutedAt(at(2, 9, 30))) // Friday
    }

    @Test
    fun `a window past midnight belongs to the day it starts on`() {
        val schedule = MuteSchedule(listOf(window(DayOfWeek.FRIDAY, from = "22:00", to = "07:00")))

        assertFalse(schedule.mutedAt(at(1, 23, 0))) // Thursday night
        assertTrue(schedule.mutedAt(at(2, 22, 0)))
        assertTrue(schedule.mutedAt(at(3, 6, 59))) // Saturday morning
        assertFalse(schedule.mutedAt(at(3, 7, 0)))
        assertFalse(schedule.mutedAt(at(2, 6, 0))) // Friday morning is Thursday's night
    }

    @Test
    fun `a window with no days or equal times mutes nothing`() {
        assertFalse(MuteSchedule(listOf(window(from = "09:00", to = "10:00"))).mutedAt(at(1, 9, 30)))
        assertFalse(MuteSchedule(listOf(window(DayOfWeek.THURSDAY, from = "09:00", to = "09:00"))).mutedAt(at(1, 9, 0)))
        assertNull(
            MuteSchedule(listOf(window(DayOfWeek.THURSDAY, from = "09:00", to = "09:00"))).untilChange(at(1, 9, 0)),
        )
    }

    @Test
    fun `untilChange finds the next start or end, also a week ahead`() {
        val schedule = MuteSchedule(listOf(window(DayOfWeek.THURSDAY, from = "09:00", to = "10:00")))

        assertEquals(Duration.ofMinutes(30), schedule.untilChange(at(1, 8, 30)))
        assertEquals(Duration.ofMinutes(30), schedule.untilChange(at(1, 9, 30)))
        assertEquals(Duration.ofDays(7).minusHours(1), schedule.untilChange(at(1, 10, 0)))
    }

    @Test
    fun `the windows follow the phone's wall clock into another zone`() {
        val schedule = MuteSchedule(listOf(window(DayOfWeek.THURSDAY, from = "22:00", to = "23:00")))
        val kyiv = ZoneId.of("Europe/Kyiv") // UTC+3 in October 2026 until the 25th

        assertTrue(schedule.mutedAt(ZonedDateTime.of(LocalDateTime.of(2026, 10, 1, 22, 30), kyiv)))
        assertFalse(
            schedule.mutedAt(ZonedDateTime.of(LocalDateTime.of(2026, 10, 1, 22, 30), utc).withZoneSameInstant(kyiv)),
        )
    }

    @Test
    fun `encode and decode round trip, and damaged text loses only the damaged window`() {
        val schedule =
            MuteSchedule(
                listOf(
                    window(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, from = "22:00", to = "07:30"),
                    window(DayOfWeek.WEDNESDAY, from = "13:15", to = "14:00"),
                ),
            )

        assertEquals(schedule, MuteSchedule.decode(schedule.encode()))
        assertEquals(MuteSchedule(), MuteSchedule.decode(null))
        assertEquals(
            schedule.windows.drop(1),
            MuteSchedule
                .decode(
                    "junk;" + schedule.windows[1].let { MuteSchedule(listOf(it)).encode() },
                ).windows,
        )
        assertEquals(MuteSchedule(), MuteSchedule.decode("4,9999,10"))
    }
}
