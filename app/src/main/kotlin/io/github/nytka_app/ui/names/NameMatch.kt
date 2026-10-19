package io.github.nytka_app.ui.names

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.nytka_app.core.api.Person
import java.text.Normalizer

/** How many names a field offers at once. */
const val MAX_SUGGESTIONS = 3

/** The fewest letters of a word in free text before names are offered for it. */
private const val MIN_WORD = 2

private const val RANK_START = 0
private const val RANK_WORD = 1
private const val RANK_INSIDE = 2

/**
 * Names for a field that holds one name: the server's [guesses] first, then known people whose name starts with
 * [typed], then those with a later word that starts with it, then those that contain it; the most recently heard first
 * within each. An empty field offers the guesses and the people heard last. A guess takes the spelling of the known
 * person it names. The name already typed is never offered (Zoë still is for "zoe"), nor a person known only by a role.
 */
fun nameSuggestions(
    people: List<Person>,
    typed: String,
    guesses: List<String> = emptyList(),
    limit: Int = MAX_SUGGESTIONS,
): List<String> {
    val query = fold(typed.trim())
    val matched =
        recentFirst(people.filter { it.named })
            .mapNotNull { person -> rankOf(person.name, query)?.let { person.name to it } }
            .sortedBy { it.second }
            .map { it.first }
    val spelling = people.associate { fold(it.name) to it.name }
    val fitting =
        guesses
            .filter { query.isEmpty() || rankOf(it, query) != null }
            .map { spelling[fold(it)] ?: it }
    return (fitting + matched)
        .distinctBy(::fold)
        .filterNot { it.equals(typed.trim(), ignoreCase = true) }
        .take(limit)
}

/**
 * Names for the word right before the cursor in free text, as in "met Ol|": known people with a word that starts with
 * it, the most recently heard first. A word of [MIN_WORD] letters or more asks; in prose ([capitalOnly]) it must also
 * start with a capital letter, so ordinary words do not. A selection or a cursor inside a word asks nothing.
 */
fun completions(
    people: List<Person>,
    value: TextFieldValue,
    capitalOnly: Boolean = true,
    limit: Int = MAX_SUGGESTIONS,
): List<String> {
    val word = wordBeforeCursor(value) ?: return emptyList()
    val text = value.text.substring(word.first, word.last + 1)
    if (text.length < MIN_WORD || (capitalOnly && !text.first().isUpperCase())) return emptyList()
    val query = fold(text)
    return recentFirst(people.filter { it.named })
        .map { it.name }
        .filter { name -> !name.equals(text, ignoreCase = true) && words(name).any { it.startsWith(query) } }
        .distinctBy(::fold)
        .take(limit)
}

/**
 * [value] with the word before the cursor replaced by [name]. At the end of the text a space follows, so typing goes
 * on; the cursor lands after the name and any space already there.
 */
fun complete(
    value: TextFieldValue,
    name: String,
): TextFieldValue {
    val word = wordBeforeCursor(value) ?: return value
    val before = value.text.substring(0, word.first)
    val after = value.text.substring(word.last + 1)
    val inserted = if (after.isEmpty()) "$name " else name
    val cursor = before.length + inserted.length + if (after.startsWith(" ")) 1 else 0
    return TextFieldValue(before + inserted + after, TextRange(cursor))
}

/** A whole field set to [name], with the cursor at its end. */
fun filled(name: String): TextFieldValue = TextFieldValue(name, TextRange(name.length))

/** The letters right before a collapsed cursor that does not sit inside a word. */
private fun wordBeforeCursor(value: TextFieldValue): IntRange? {
    if (!value.selection.collapsed) return null
    val end = value.selection.start
    val text = value.text
    if (end < text.length && isNamePart(text[end])) return null
    var start = end
    while (start > 0 && isNamePart(text[start - 1])) start--
    return if (start == end) null else start until end
}

private fun isNamePart(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == '-'

private fun rankOf(
    name: String,
    query: String,
): Int? {
    val folded = fold(name)
    return when {
        query.isEmpty() -> RANK_START
        folded.startsWith(query) -> RANK_START
        words(name).any { it.startsWith(query) } -> RANK_WORD
        folded.contains(query) -> RANK_INSIDE
        else -> null
    }
}

private fun recentFirst(people: List<Person>) = people.sortedByDescending { it.lastSeenAt.orEmpty() }

private fun words(name: String) = fold(name).split(' ', '-').filter(String::isNotEmpty)

/** Lower case without accents, so "zoe" finds Zoë; Ukrainian й and ї fold to и and і the same way. */
private fun fold(text: String): String =
    Normalizer
        .normalize(text.lowercase(), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
