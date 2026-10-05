package io.github.nytka_app.ui.people

/** How a name suggestion reads. A role is shown as the server sends it ("repairman", "майстер"). */
sealed interface SuggestionWording {
    data class Name(
        val name: String,
    ) : SuggestionWording

    /** A role and no name yet: accepting creates a person known by the role. */
    data class RoleOnly(
        val role: String,
    ) : SuggestionWording

    data class NameAndRole(
        val name: String,
        val role: String,
    ) : SuggestionWording

    companion object {
        /** [name] is the display form of the role when [named] is false. A server without a role sends none. */
        fun of(
            name: String,
            role: String?,
            named: Boolean,
        ): SuggestionWording {
            val shown = role?.trim()?.takeIf(String::isNotEmpty) ?: return Name(name)
            return if (named) NameAndRole(name, shown) else RoleOnly(shown)
        }
    }
}

/** What accepting a name came to beyond the name itself. Carries a person name for the screen to word. */
sealed interface RoleNotice {
    /** The server joined the person known by a role to the person who already had [name]. */
    data class MergedInto(
        val name: String,
    ) : RoleNotice
}

/**
 * True when accepting a name ended with a person other than the one the suggestion pointed at: the server merged a
 * person known by a role into the one who had the name.
 */
internal fun merged(
    suggestedPersonId: String?,
    answeredPersonId: String?,
): Boolean = suggestedPersonId != null && answeredPersonId != null && suggestedPersonId != answeredPersonId
