package io.github.nytka_app.ui.people

import io.github.nytka_app.core.api.NameSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AutoNamesTest {
    private val speaker = NameSuggestion("a1", target = "speaker", speakerId = "sp1", personId = "p1")
    private val label = NameSuggestion("a2", target = "label", personId = "p2")

    @Test
    fun `a speaker's name marks that voice, any other target the person`() {
        assertTrue(speaker.names("sp1", null))
        assertFalse(speaker.names("sp2", "p1"))
        assertTrue(label.names(null, "p2"))
        assertFalse(label.names("sp1", null))
        assertFalse(NameSuggestion("a3", target = "label").names(null, null))
    }

    @Test
    fun `recent keeps the last seven days and names without a time`() {
        val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
        val fresh = speaker.copy(decidedAt = "2026-10-14T12:00:00Z")
        val stale = speaker.copy(id = "a4", decidedAt = "2026-10-13T11:59:00Z")
        val untimed = speaker.copy(id = "a5")

        assertEquals(listOf(fresh, untimed), listOf(fresh, stale, untimed).recent(clock))
    }
}
