package io.github.nytka_app.ui.people

import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.VoiceCard
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonRowTest {
    private val people = listOf("a", "b", "c").map { Person(id = it, name = it) }

    @Test
    fun `people with an open match come first and keep their order`() {
        val cards =
            listOf(
                VoiceCard(kind = VoiceCard.KIND_MATCH, id = "m1", personId = "c"),
                VoiceCard(kind = VoiceCard.KIND_GROUP, id = "g1"),
            )

        val ordered = openFirst(people, peopleWithOpenMatch(cards))

        assertEquals(listOf("c", "a", "b"), ordered.map { it.id })
    }

    @Test
    fun `without a match the order is untouched`() {
        assertEquals(people, openFirst(people, emptySet()))
    }
}
