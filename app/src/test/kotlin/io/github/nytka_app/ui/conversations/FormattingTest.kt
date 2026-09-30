package io.github.nytka_app.ui.conversations

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FormattingTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun `days read as today, yesterday or a date`() {
        assertEquals("Today", Formatting.dayTitle(today, today))
        assertEquals("Yesterday", Formatting.dayTitle(today.minusDays(1), today))
        assertEquals("Friday, 25 September", Formatting.dayTitle(LocalDate.of(2026, 9, 25), today))
    }

    @Test
    fun `times are local`() {
        val start = Instant.parse("2026-09-29T08:00:00Z")
        val end = Instant.parse("2026-09-29T08:15:30Z")

        assertEquals("11:00–11:15", Formatting.timeRange(start, end, kyiv))
        assertEquals("11:00", Formatting.clock(start, kyiv))
    }

    @Test
    fun `lengths round to minutes`() {
        val start = Instant.parse("2026-09-29T08:00:00Z")

        assertEquals("under a minute", Formatting.length(start, start.plusSeconds(40)))
        assertEquals("15 min", Formatting.length(start, start.plusSeconds(15 * 60 + 29)))
        assertEquals("1 h 5 min", Formatting.length(start, start.plusSeconds(65 * 60)))
    }

    @Test
    fun `a playback position reads as minutes and seconds, with hours from an hour on`() {
        assertEquals("0:00", Formatting.position(0))
        assertEquals("1:05", Formatting.position(65_900))
        assertEquals("1:02:05", Formatting.position(3_725_000))
    }
}
