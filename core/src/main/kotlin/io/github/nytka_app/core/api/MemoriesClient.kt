package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** A lasting fact about the user. [source] is `ai` or `user`; the conversation fields are null without a source. */
@Serializable
data class Memory(
    val id: String,
    val text: String,
    val source: String,
    val conversationId: String? = null,
    val conversationTitle: String? = null,
    val conversationStartedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class MemoryPage(
    val items: List<Memory>,
    val nextBefore: String? = null,
)

/** The memories endpoints of v0.4, apart so the tab can be tested without HTTP. A server without them answers 404. */
interface MemoriesClient {
    suspend fun memories(
        before: String?,
        limit: Int,
    ): ApiResult<MemoryPage>

    suspend fun addMemory(text: String): ApiResult<Memory>

    suspend fun editMemory(
        id: String,
        text: String,
    ): ApiResult<Memory>

    suspend fun deleteMemory(id: String): ApiResult<Unit>
}

@Serializable
private data class MemoryBody(
    val text: String,
)

class MemoriesApi(
    private val api: NytkaApi,
) : MemoriesClient {
    override suspend fun memories(
        before: String?,
        limit: Int,
    ): ApiResult<MemoryPage> =
        api.request("GET", "api/v1/memories", mapOf("before" to before, "limit" to limit.toString())) {
            api.json.decodeFromString(it)
        }

    override suspend fun addMemory(text: String): ApiResult<Memory> =
        api.request("POST", "api/v1/memories", body = api.json.encodeToString(MemoryBody(text))) {
            api.json.decodeFromString(it)
        }

    override suspend fun editMemory(
        id: String,
        text: String,
    ): ApiResult<Memory> =
        api.request("PATCH", "api/v1/memories/$id", body = api.json.encodeToString(MemoryBody(text))) {
            api.json.decodeFromString(it)
        }

    override suspend fun deleteMemory(id: String): ApiResult<Unit> = api.request("DELETE", "api/v1/memories/$id") { }
}
