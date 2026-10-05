package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/** A tag in use with its counts; [uses] is [conversations] plus [people]. */
@Serializable
data class Tag(
    val name: String,
    val conversations: Int = 0,
    val people: Int = 0,
    val uses: Int = 0,
)

@Serializable
internal data class TagList(
    val items: List<Tag> = emptyList(),
)

@Serializable
internal data class TagPeople(
    val items: List<Person> = emptyList(),
)

@Serializable
internal data class TagSuggestionList(
    val items: List<TagSuggestion> = emptyList(),
)

/** The tags of an item after an add or a remove, sorted by the server. */
@Serializable
internal data class ItemTags(
    val tags: List<String> = emptyList(),
)

/**
 * A tag the model proposed. [personId] is null for a conversation's tag and set for a person's; [conversationId] is
 * where it came from either way.
 */
@Serializable
data class TagSuggestion(
    val id: String,
    val conversationId: String = "",
    val personId: String? = null,
    val personName: String? = null,
    val name: String = "",
    val createdAt: String = "",
)
