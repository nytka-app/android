package io.github.nytka_app.ui.ask

import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class AskIdeasTest {
    @Test
    fun `the two people heard last get a question each about the last talk and what is owed, then two general ones`() {
        val people =
            listOf(
                Person("p1", "Olena", lastSeenAt = "2026-10-01T10:00:00Z"),
                Person("p2", "Oleh", lastSeenAt = "2026-10-18T10:00:00Z"),
                Person("p3", "Mykola", lastSeenAt = "2026-10-10T10:00:00Z"),
                Person("p4", "Repairman", named = false, lastSeenAt = "2026-10-19T10:00:00Z"),
            )

        assertEquals(
            listOf(
                AskIdea(R.string.ask_idea_last_format, "Oleh"),
                AskIdea(R.string.ask_idea_promised_format, "Oleh"),
                AskIdea(R.string.ask_idea_last_format, "Mykola"),
                AskIdea(R.string.ask_idea_promised_format, "Mykola"),
                AskIdea(R.string.ask_idea_yesterday),
                AskIdea(R.string.ask_idea_open),
            ),
            askIdeas(people),
        )
    }

    @Test
    fun `without people only the general questions show`() {
        assertEquals(
            listOf(AskIdea(R.string.ask_idea_yesterday), AskIdea(R.string.ask_idea_open)),
            askIdeas(emptyList()),
        )
    }
}
