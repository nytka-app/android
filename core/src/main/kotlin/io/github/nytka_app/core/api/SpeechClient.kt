package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * What kind of speech a transcript line is (server feature `speech-kind`): the owner's marks on one line or on every
 * other voice of a conversation. Apart from [ConversationsClient] so its fakes stay. A [kind] is "person", "media"
 * or "call"; null clears the mark, and is sent as JSON `null`. Every write needs an admin token
 * ([FailureKind.Forbidden] otherwise).
 */
interface SpeechClient {
    /** `PATCH api/v1/segments/{id}`, any line, the wearer's included; answers the segment as it stands now. */
    suspend fun markSegment(
        id: Long,
        kind: String?,
    ): ApiResult<Segment>

    /** Marks every line that is not the wearer's; answers how many lines changed. */
    suspend fun markConversation(
        id: String,
        kind: String?,
    ): ApiResult<Int>
}

class SpeechApi(
    private val api: NytkaApi,
) : SpeechClient {
    override suspend fun markSegment(
        id: Long,
        kind: String?,
    ): ApiResult<Segment> =
        api.request(
            "PATCH",
            "api/v1/segments/$id",
            body = buildJsonObject { put("speechKind", kind?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { api.json.decodeFromString(it) }

    override suspend fun markConversation(
        id: String,
        kind: String?,
    ): ApiResult<Int> =
        api.request(
            "POST",
            "api/v1/conversations/$id/speech",
            body = buildJsonObject { put("kind", kind?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { api.json.decodeFromString<Marked>(it).marked }

    @Serializable
    private data class Marked(
        val marked: Int,
    )
}
