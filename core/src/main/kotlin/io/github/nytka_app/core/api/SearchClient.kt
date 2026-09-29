package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/**
 * One search hit. [kind] is `conversation` or `memory`; [title] is null for a memory; [snippet] is plain text
 * with the matches in `<mark>`; [conversationId] is set for a conversation and for a memory that has a source.
 */
@Serializable
data class Hit(
    val kind: String,
    val id: String,
    val score: Double = 0.0,
    val title: String? = null,
    val snippet: String = "",
    val at: String,
    val conversationId: String? = null,
)

@Serializable
data class SearchPage(
    val items: List<Hit>,
    val nextOffset: Int? = null,
)

/** Full-text search of v0.4; a server without it answers 404. */
interface SearchClient {
    /** [kinds] holds `conversation`, `memory` or both; empty means the server's default, both. */
    suspend fun search(
        query: String,
        kinds: Set<String>,
        limit: Int,
        offset: Int,
    ): ApiResult<SearchPage>
}

class SearchApi(
    private val api: NytkaApi,
) : SearchClient {
    override suspend fun search(
        query: String,
        kinds: Set<String>,
        limit: Int,
        offset: Int,
    ): ApiResult<SearchPage> =
        api.request(
            "GET",
            "api/v1/search",
            mapOf(
                "q" to query,
                "kinds" to kinds.sorted().joinToString(",").ifEmpty { null },
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        ) { api.json.decodeFromString(it) }
}
