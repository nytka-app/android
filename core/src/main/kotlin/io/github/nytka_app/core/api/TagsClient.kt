package io.github.nytka_app.core.api

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Tags on conversations and people (server feature `tags`) and the tags the model proposes (`tag-suggestions`).
 * Apart from [ConversationsClient] and [PeopleClient] so their fakes stay. A name is sent as typed: the server
 * normalizes it and its answer is the truth; a bad one is [FailureKind.Invalid], a 21st tag [FailureKind.Conflict].
 * Every write needs an admin token ([FailureKind.Forbidden] otherwise).
 */
interface TagsClient {
    /** The tags in use, most used first; [q] keeps those that start with it. */
    suspend fun tags(q: String?): ApiResult<List<Tag>>

    /** Each of these answers the item's tags as the server holds them now; removing one it lacks is also `Ok`. */
    suspend fun addConversationTag(
        id: String,
        name: String,
    ): ApiResult<List<String>>

    suspend fun removeConversationTag(
        id: String,
        name: String,
    ): ApiResult<List<String>>

    suspend fun addPersonTag(
        id: String,
        name: String,
    ): ApiResult<List<String>>

    suspend fun removePersonTag(
        id: String,
        name: String,
    ): ApiResult<List<String>>

    /** The conversations list with `tag`; [before] is the previous page's `nextBefore`. */
    suspend fun conversations(
        tag: String,
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage>

    suspend fun people(tag: String): ApiResult<List<Person>>

    /** The pending proposals, newest first, at most 200; the server route comes with server T-4. */
    suspend fun suggestions(): ApiResult<List<TagSuggestion>>

    /** One call per tap, never made by itself; [FailureKind.Conflict] when no longer pending or the item is full. */
    suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit>
}

class TagsApi(
    private val api: NytkaApi,
) : TagsClient {
    override suspend fun tags(q: String?): ApiResult<List<Tag>> =
        api.request("GET", "api/v1/tags", mapOf("q" to q?.takeIf { it.isNotEmpty() })) {
            api.json.decodeFromString<TagList>(it).items
        }

    override suspend fun addConversationTag(
        id: String,
        name: String,
    ): ApiResult<List<String>> = itemTags("PUT", "conversations", id, name)

    override suspend fun removeConversationTag(
        id: String,
        name: String,
    ): ApiResult<List<String>> = itemTags("DELETE", "conversations", id, name)

    override suspend fun addPersonTag(
        id: String,
        name: String,
    ): ApiResult<List<String>> = itemTags("PUT", "people", id, name)

    override suspend fun removePersonTag(
        id: String,
        name: String,
    ): ApiResult<List<String>> = itemTags("DELETE", "people", id, name)

    override suspend fun conversations(
        tag: String,
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> =
        api.request(
            "GET",
            "api/v1/conversations",
            mapOf("tag" to tag, "before" to before, "limit" to limit.toString()),
        ) { api.json.decodeFromString(it) }

    override suspend fun people(tag: String): ApiResult<List<Person>> =
        api.request("GET", "api/v1/people", mapOf("tag" to tag)) {
            api.json.decodeFromString<TagPeople>(it).items
        }

    override suspend fun suggestions(): ApiResult<List<TagSuggestion>> =
        api.request("GET", "api/v1/tags/suggestions", mapOf("status" to "pending")) {
            api.json.decodeFromString<TagSuggestionList>(it).items
        }

    override suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> = api.request("POST", "api/v1/tags/suggestions/$id/${if (accept) "accept" else "reject"}") { }

    private suspend fun itemTags(
        method: String,
        area: String,
        id: String,
        name: String,
    ): ApiResult<List<String>> =
        api.request(method, "api/v1/$area/$id/tags/${segment(name)}") {
            api.json.decodeFromString<ItemTags>(it).tags
        }

    /** One percent-encoded path segment: `request` takes the path as it goes on the wire, and a tag may be Cyrillic. */
    private fun segment(name: String): String =
        "http://localhost/"
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(name)
            .build()
            .encodedPathSegments
            .first()
}
