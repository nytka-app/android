package io.github.nytka_app.core.api

/** The reading side of the API, apart so the screens can be tested without HTTP. */
interface ConversationsClient {
    suspend fun conversations(
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage>

    suspend fun conversation(id: String): ApiResult<ConversationDetail>

    /** [title] null restores the generated one; a server before v0.2 answers [FailureKind.NotFound]. */
    suspend fun renameConversation(
        id: String,
        title: String?,
    ): ApiResult<ConversationDetail>

    /** Names the voice [speakerId] [name]; a server before v0.6 answers [FailureKind.NotFound]. */
    suspend fun nameVoice(
        speakerId: String,
        name: String,
    ): ApiResult<Unit>

    /**
     * The wearer's own mark on a segment: true is "this is me", false "this is not me", null clears the mark. Answers
     * the segment as the conversation shows it; a server before 0.12 answers [FailureKind.NotFound].
     */
    suspend fun markSegment(
        segmentId: Long,
        isUser: Boolean?,
    ): ApiResult<Segment>

    /** Queues a new summary run; [FailureKind.Conflict] while the conversation is open or no model is set up. */
    suspend fun enrichConversation(id: String): ApiResult<Unit>

    suspend fun deleteConversation(id: String): ApiResult<Unit>

    suspend fun transcriptionsJson(id: String): ApiResult<String>
}
