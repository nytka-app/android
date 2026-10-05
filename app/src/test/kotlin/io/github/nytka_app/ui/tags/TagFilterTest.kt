package io.github.nytka_app.ui.tags

import io.github.nytka_app.core.api.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class TagFilterTest {
    private val tags = listOf(Tag("work", 3, 1, 4), Tag("repairman", 0, 2, 2), Tag("family", 1, 0, 1))

    @Test
    fun `the Conversations picker lists the tags that have conversations, with their counts`() {
        val options = tags.filterOptions { it.conversations }

        assertEquals(listOf("work" to 3, "family" to 1), options.map { it.name to it.count })
    }

    @Test
    fun `the People picker lists the tags that have people, with their counts`() {
        val options = tags.filterOptions { it.people }

        assertEquals(listOf("work" to 1, "repairman" to 2), options.map { it.name to it.count })
    }

    @Test
    fun `the picker keeps the server's order, most used first`() {
        assertEquals(listOf("work", "repairman", "family"), tags.filterOptions { it.uses }.map { it.name })
    }
}
