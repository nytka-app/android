package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class ReviewList(
    val items: List<ReviewItem> = emptyList(),
)

@Serializable
private data class SuggestionList(
    val items: List<NameSuggestion> = emptyList(),
)

@Serializable
private data class PersonRef(
    val id: String? = null,
)

@Serializable
private data class AcceptedByNameBody(
    val person: PersonRef? = null,
    val accepted: Int = 0,
    val skipped: Int = 0,
)

/**
 * The review inbox (server after 0.14) and the name suggestions (0.14). Nothing here applies itself: each answer is
 * one call, made on a tap. A server without a route answers [FailureKind.NotFound] or [FailureKind.Unsupported];
 * [FailureKind.Conflict] on an answer means the suggestion was no longer pending.
 */
interface ReviewClient {
    /** Newest first; [limit] is 1 to 200. */
    suspend fun review(limit: Int): ApiResult<List<ReviewItem>>

    /** [kind] and [id] are those of the [ReviewItem]; accept and reject both answer `Ok` on `200` and `204`. */
    suspend fun answer(
        kind: String,
        id: String,
        accept: Boolean,
    ): ApiResult<Unit>

    /** The pending suggestions, newest first, at most 200. */
    suspend fun suggestions(): ApiResult<List<NameSuggestion>>

    suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit>

    /**
     * Accepts suggestion [id] and answers the id of the person it ended with (null when the body has none). It differs
     * from the suggestion's own `personId` when the server merged a person known by a role into one who had the name.
     */
    suspend fun acceptSuggestion(id: String): ApiResult<String?> =
        answerSuggestion(id, accept = true).let { if (it is ApiResult.Failure) it else ApiResult.Ok(null) }

    /**
     * Accepts every pending suggestion of [name] (admin, server 0.21 and later), all or nothing. [FailureKind.NotFound]
     * when none is pending, or on a server without the route; [FailureKind.Conflict] when they disagree or the name is
     * that of a person known only by a role.
     */
    suspend fun acceptAllByName(name: String): ApiResult<AcceptedByName>

    /** As [acceptSuggestion] for the inbox item [kind] and [id]. */
    suspend fun acceptReview(
        kind: String,
        id: String,
    ): ApiResult<String?> =
        answer(kind, id, accept = true).let { if (it is ApiResult.Failure) it else ApiResult.Ok(null) }
}

class ReviewApi(
    private val api: NytkaApi,
) : ReviewClient {
    override suspend fun review(limit: Int): ApiResult<List<ReviewItem>> =
        api.request("GET", "api/v1/review", mapOf("limit" to limit.toString())) {
            api.json.decodeFromString<ReviewList>(it).items
        }

    override suspend fun answer(
        kind: String,
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> = api.request("POST", "api/v1/review/$kind/$id/${verb(accept)}") { }

    override suspend fun suggestions(): ApiResult<List<NameSuggestion>> =
        api.request("GET", "api/v1/people/suggestions", mapOf("status" to "pending")) {
            api.json.decodeFromString<SuggestionList>(it).items
        }

    override suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> = api.request("POST", "api/v1/people/suggestions/$id/${verb(accept)}") { }

    override suspend fun acceptSuggestion(id: String): ApiResult<String?> =
        api.request("POST", "api/v1/people/suggestions/$id/accept") { personId(it) }

    override suspend fun acceptAllByName(name: String): ApiResult<AcceptedByName> =
        api.request(
            "POST",
            "api/v1/people/suggestions/accept-by-name",
            body = buildJsonObject { put("name", name) }.toString(),
        ) {
            val body = api.json.decodeFromString<AcceptedByNameBody>(it)
            AcceptedByName(body.person?.id, body.accepted, body.skipped)
        }

    override suspend fun acceptReview(
        kind: String,
        id: String,
    ): ApiResult<String?> = api.request("POST", "api/v1/review/$kind/$id/accept") { personId(it) }

    private fun personId(body: String): String? =
        runCatching { api.json.decodeFromString<PersonRef>(body).id }.getOrNull()

    private fun verb(accept: Boolean) = if (accept) "accept" else "reject"
}
