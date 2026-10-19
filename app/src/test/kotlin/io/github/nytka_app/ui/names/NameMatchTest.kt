package io.github.nytka_app.ui.names

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.nytka_app.core.api.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class NameMatchTest {
    private val people =
        listOf(
            Person("p1", "Olena Petrenko", lastSeenAt = "2026-10-01T10:00:00Z"),
            Person("p2", "Oleh", lastSeenAt = "2026-10-18T10:00:00Z"),
            Person("p3", "Mykola", lastSeenAt = "2026-10-10T10:00:00Z"),
            Person("p4", "Anna-Olesia", lastSeenAt = "2026-10-19T10:00:00Z"),
            Person("p5", "Repairman", named = false, lastSeenAt = "2026-10-19T11:00:00Z"),
            Person("p6", "Zoë"),
            Person("p7", "Nikol", lastSeenAt = "2026-09-01T10:00:00Z"),
        )

    private fun at(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun `an empty name field offers the people heard last, without roles`() {
        assertEquals(listOf("Anna-Olesia", "Oleh", "Mykola"), nameSuggestions(people, ""))
    }

    @Test
    fun `names that start with what is typed come before later words and before inner matches`() {
        assertEquals(listOf("Oleh", "Olena Petrenko", "Anna-Olesia"), nameSuggestions(people, "ole"))
        assertEquals(listOf("Mykola", "Nikol"), nameSuggestions(people, "kol"))
    }

    @Test
    fun `the server's guesses come first, but only when they fit what is typed`() {
        assertEquals(listOf("Ivan", "Anna-Olesia", "Oleh"), nameSuggestions(people, "", guesses = listOf("Ivan")))
        assertEquals(listOf("Mykola"), nameSuggestions(people, "my", guesses = listOf("Ivan")))
        assertEquals(listOf("Oleh", "Olena Petrenko", "Anna-Olesia"), nameSuggestions(people, "ole", listOf("oleh")))
    }

    @Test
    fun `the name already typed is not offered, and accents do not matter`() {
        assertEquals(emptyList<String>(), nameSuggestions(people, "Mykola"))
        assertEquals(listOf("Zoë"), nameSuggestions(people, "zoe"))
    }

    @Test
    fun `a capitalised word before the cursor gets the names with a word that starts with it`() {
        assertEquals(listOf("Anna-Olesia", "Oleh", "Olena Petrenko"), completions(people, at("Call Ol")))
        assertEquals(listOf("Olena Petrenko"), completions(people, at("met Pet")))
    }

    @Test
    fun `ordinary words, one letter, a selection and a cursor inside a word ask nothing`() {
        assertEquals(emptyList<String>(), completions(people, at("call ol")))
        assertEquals(emptyList<String>(), completions(people, at("Call O")))
        assertEquals(emptyList<String>(), completions(people, TextFieldValue("Call Ol", TextRange(5, 7))))
        assertEquals(emptyList<String>(), completions(people, TextFieldValue("Call Olx", TextRange(7))))
        assertEquals(emptyList<String>(), completions(people, at("Call Oleh")))
    }

    @Test
    fun `a search field completes lower-case words too`() {
        assertEquals(listOf("Mykola"), completions(people, at("my"), capitalOnly = false))
    }

    @Test
    fun `completing replaces the word, adds a space at the end and puts the cursor after it`() {
        assertEquals(at("Call Olena Petrenko "), complete(at("Call Ol"), "Olena Petrenko"))
    }

    @Test
    fun `completing inside the text keeps what follows and adds no space before punctuation`() {
        assertEquals(
            TextFieldValue("Ask Oleh, then", TextRange(8)),
            complete(TextFieldValue("Ask Ol, then", TextRange(6)), "Oleh"),
        )
        assertEquals(
            TextFieldValue("Ask Oleh about it", TextRange(9)),
            complete(TextFieldValue("Ask Ol about it", TextRange(6)), "Oleh"),
        )
    }
}
