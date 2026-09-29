package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.ConversationsClient

class FakeConversations : ConversationsClient {
    val pages = mutableMapOf<String?, ApiResult<ConversationPage>>()
    var detail: ApiResult<ConversationDetail>? = null
    var raw: ApiResult<String> = ApiResult.Ok("{}")
    val requestedBefore = mutableListOf<String?>()
    val deleted = mutableListOf<String>()

    override suspend fun conversations(
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> {
        requestedBefore += before
        return pages.getValue(before)
    }

    override suspend fun conversation(id: String) = detail!!

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
