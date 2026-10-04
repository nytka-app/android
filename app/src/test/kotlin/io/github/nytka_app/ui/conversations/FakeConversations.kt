package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.Segment
import kotlinx.coroutines.CompletableDeferred

class FakeConversations : ConversationsClient {
    val pages = mutableMapOf<String?, ApiResult<ConversationPage>>()
    var detail: ApiResult<ConversationDetail>? = null
    var raw: ApiResult<String> = ApiResult.Ok("{}")
    val requestedBefore = mutableListOf<String?>()
    val deleted = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String?>>()
    val enriched = mutableListOf<String>()
    val named = mutableListOf<Pair<String, String>>()
    var nameAnswer: ApiResult<Unit> = ApiResult.Ok(Unit)
    var renameAnswer: ApiResult<ConversationDetail>? = null
    val marked = mutableListOf<Pair<Long, Boolean?>>()
    var markAnswer: suspend (Long, Boolean?) -> ApiResult<Segment> = { _, _ -> error("No answer set.") }
    var enrichAnswer: ApiResult<Unit> = ApiResult.Ok(Unit)

    /** Each gate holds back the next request's answer, which is fixed when the request comes in, until it completes. */
    val gates = ArrayDeque<CompletableDeferred<Unit>>()

    override suspend fun conversations(
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> {
        requestedBefore += before
        val answer = pages.getValue(before)
        gates.removeFirstOrNull()?.await()
        return answer
    }

    override suspend fun conversation(id: String) = detail!!

    override suspend fun renameConversation(
        id: String,
        title: String?,
    ): ApiResult<ConversationDetail> {
        renamed += id to title
        return renameAnswer ?: ApiResult.Ok(detail.let { (it as ApiResult.Ok).value.copy(title = title) })
    }

    override suspend fun nameVoice(
        speakerId: String,
        name: String,
    ): ApiResult<Unit> = nameAnswer.also { named += speakerId to name }

    override suspend fun markSegment(
        segmentId: Long,
        isUser: Boolean?,
    ): ApiResult<Segment> {
        marked += segmentId to isUser
        return markAnswer(segmentId, isUser)
    }

    override suspend fun enrichConversation(id: String): ApiResult<Unit> = enrichAnswer.also { enriched += id }

    override suspend fun deleteConversation(id: String): ApiResult<Unit> = ApiResult.Ok(Unit).also { deleted += id }

    override suspend fun transcriptionsJson(id: String) = raw

    companion object {
        fun summary(
            id: String,
            start: String,
            end: String,
            preview: String = "text $id",
        ) = ConversationSummary(id, start, end, "closed", preview)
    }
}
