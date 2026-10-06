package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.core.api.SpeechClient
import kotlinx.coroutines.CompletableDeferred

/** Calls are recorded as `"segment 1 media"` and `"conversation c1 null"`; each answer is what the test set. */
class FakeSpeech : SpeechClient {
    val calls = mutableListOf<String>()
    var segmentAnswer: ApiResult<Segment>? = null
    var conversationAnswer: ApiResult<Int> = ApiResult.Ok(0)

    /** The `media=hide` list, by `before`; each request is recorded as `"tag before"`. */
    val hiddenPages = mutableMapOf<String?, ApiResult<ConversationPage>>()
    val hiddenRequests = mutableListOf<Pair<String?, String?>>()

    /** Holds back the answer of the next segment mark until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun markSegment(
        id: Long,
        kind: String?,
    ): ApiResult<Segment> {
        calls += "segment $id $kind"
        gate?.await()
        return segmentAnswer ?: error("No answer set.")
    }

    override suspend fun markConversation(
        id: String,
        kind: String?,
    ): ApiResult<Int> {
        calls += "conversation $id $kind"
        return conversationAnswer
    }

    override suspend fun conversationsWithoutMedia(
        tag: String?,
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> {
        hiddenRequests += tag to before
        return hiddenPages.getValue(before)
    }
}
