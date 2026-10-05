package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.core.api.SpeechClient
import kotlinx.coroutines.CompletableDeferred

/** Calls are recorded as `"segment 1 media"` and `"conversation c1 null"`; each answer is what the test set. */
class FakeSpeech : SpeechClient {
    val calls = mutableListOf<String>()
    var segmentAnswer: ApiResult<Segment>? = null
    var conversationAnswer: ApiResult<Int> = ApiResult.Ok(0)

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
}
