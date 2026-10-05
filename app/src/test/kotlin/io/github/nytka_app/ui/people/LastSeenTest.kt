package io.github.nytka_app.ui.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class LastSeenTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val zone = ZoneOffset.UTC

    @Test
    fun `a time today is the clock time`() {
        assertEquals(
            LastSeen.Today(LocalTime.of(14, 3)),
            LastSeen.of("2026-10-05T14:03:00Z", today, zone),
        )
    }

    @Test
    fun `yesterday and older days`() {
        assertEquals(LastSeen.Yesterday, LastSeen.of("2026-10-04T23:00:00Z", today, zone))
        assertEquals(LastSeen.On(LocalDate.of(2026, 10, 3)), LastSeen.of("2026-10-03T08:00:00+00:00", today, zone))
    }

    @Test
    fun `missing or unreadable is null`() {
        assertNull(LastSeen.of(null, today, zone))
        assertNull(LastSeen.of("soon", today, zone))
    }
}
