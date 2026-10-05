package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.NameSuggestion
import io.github.nytka_app.core.api.ReviewClient
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.core.api.SuggestionEvidence

/** Only the suggestion calls are used by the conversation screen; `review` and `answer` are never made. */
class FakeReview : ReviewClient {
    var pending: ApiResult<List<NameSuggestion>> = ApiResult.Ok(emptyList())
    var answer: ApiResult<Unit> = ApiResult.Ok(Unit)
    val answered = mutableListOf<Pair<String, Boolean>>()
    var listed = 0

    /** The person id `acceptSuggestion` answers; null as for a body without one. */
    var acceptedPerson: String? = null

    override suspend fun review(limit: Int): ApiResult<List<ReviewItem>> = error("Not used.")

    override suspend fun answer(
        kind: String,
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> = error("Not used.")

    override suspend fun suggestions(): ApiResult<List<NameSuggestion>> = pending.also { listed++ }

    override suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> {
        answered += id to accept
        val result = answer
        // An answered suggestion is no longer pending, also when the server says it already was.
        if (result is ApiResult.Ok || (result as? ApiResult.Failure)?.kind == FailureKind.Conflict) {
            (pending as? ApiResult.Ok)?.let { p -> pending = ApiResult.Ok(p.value.filterNot { it.id == id }) }
        }
        return result
    }

    override suspend fun acceptSuggestion(id: String): ApiResult<String?> =
        answerSuggestion(id, accept = true).let { if (it is ApiResult.Failure) it else ApiResult.Ok(acceptedPerson) }

    companion object {
        fun suggestion(
            id: String,
            conversationId: String = "c1",
            name: String = "Olena",
            confidence: Double = 0.8,
            segmentId: Long = 1,
            role: String? = null,
            named: Boolean = true,
            personId: String? = null,
        ) = NameSuggestion(
            id = id,
            conversationId = conversationId,
            target = "speaker",
            speakerId = "sp1",
            name = name,
            confidence = confidence,
            role = role,
            named = named,
            personId = personId,
            evidence = SuggestionEvidence(segmentId, "2026-09-29T08:00:05Z", "I'm $name."),
        )
    }
}
