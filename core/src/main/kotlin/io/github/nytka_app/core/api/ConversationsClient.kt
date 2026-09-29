package io.github.nytka_app.core.api

/** The reading side of the API, apart so the screens can be tested without HTTP. */
interface ConversationsClient {
    suspend fun conversations(
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage>

    suspend fun conversation(id: String): ApiResult<ConversationDetail>

    suspend fun deleteConversation(id: String): ApiResult<Unit>

    suspend fun transcriptionsJson(id: String): ApiResult<String>
}
