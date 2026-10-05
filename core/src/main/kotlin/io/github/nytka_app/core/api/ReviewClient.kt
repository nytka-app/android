package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

@Serializable
private data class ReviewList(
    val items: List<ReviewItem> = emptyList(),
)

@Serializable
private data class SuggestionList(
    val items: List<NameSuggestion> = emptyList(),
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

    private fun verb(accept: Boolean) = if (accept) "accept" else "reject"
}
