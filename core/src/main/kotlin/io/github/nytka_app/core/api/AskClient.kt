package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A source of an answer, cited as `[n]`. [kind] is `conversation` or `memory`; [conversationId] is set for a
 * conversation and for a memory that has one; [title] is null for a memory; [snippet] is plain text.
 */
@Serializable
data class AskSource(
    val n: Int,
    val kind: String,
    val id: String,
    val conversationId: String? = null,
    val title: String? = null,
    val at: String = "",
    val snippet: String = "",
)

@Serializable
data class AskAnswer(
    val answer: String,
    val sources: List<AskSource> = emptyList(),
)

/**
 * The question endpoint of v0.8. [FailureKind.Unavailable] when the server has no model, [FailureKind.Timeout] when
 * its model call ran out of time; a server before v0.8 answers [FailureKind.NotFound] or [FailureKind.Unsupported].
 */
interface AskClient {
    suspend fun ask(question: String): ApiResult<AskAnswer>
}

/** Wrap a [NytkaApi] whose client reads for longer than the default: the model call takes up to two minutes. */
class AskApi(
    private val api: NytkaApi,
) : AskClient {
    override suspend fun ask(question: String): ApiResult<AskAnswer> =
        api.request(
            "POST",
            "api/v1/ask",
            body = buildJsonObject { put("question", question) }.toString(),
        ) { api.json.decodeFromString(it) }
}
