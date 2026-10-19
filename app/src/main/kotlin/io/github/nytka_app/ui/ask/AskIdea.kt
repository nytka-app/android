package io.github.nytka_app.ui.ask

import androidx.annotation.StringRes
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.names.nameSuggestions

/** One question the empty Ask tab offers; [name] fills the `%1$s` of [res] and is null for a question without one. */
data class AskIdea(
    @StringRes val res: Int,
    val name: String? = null,
)

private const val PEOPLE_SHOWN = 2

/**
 * Questions built from what the phone already knows: two general ones, and for the people heard last what was said
 * and what is owed. Nothing is asked until a chip is tapped.
 */
fun askIdeas(people: List<Person>): List<AskIdea> {
    val recent = nameSuggestions(people, "", limit = PEOPLE_SHOWN)
    val aboutPeople =
        recent.flatMap {
            listOf(AskIdea(R.string.ask_idea_last_format, it), AskIdea(R.string.ask_idea_promised_format, it))
        }
    return aboutPeople + listOf(AskIdea(R.string.ask_idea_yesterday), AskIdea(R.string.ask_idea_open))
}
