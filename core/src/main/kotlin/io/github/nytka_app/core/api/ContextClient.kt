package io.github.nytka_app.core.api

import io.github.nytka_app.core.queue.ContextRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** How many of a batch the server stored and how many it already had. */
@Serializable
data class RangeAnswer(
    val accepted: Int,
    val skipped: Int,
)

/** The phone-context endpoint of a server that lists `context-ranges`. */
interface ContextClient {
    /** 1 to 500 ranges; a retry after a lost answer is counted as skipped, not an error. */
    suspend fun sendRanges(items: List<ContextRow>): ApiResult<RangeAnswer>
}

class ContextApi(
    private val api: NytkaApi,
) : ContextClient {
    @Serializable
    private class Item(
        val id: String,
        val kind: String,
        val route: String,
        val startedAt: String,
        val endedAt: String,
    )

    @Serializable
    private class Body(
        val items: List<Item>,
    )

    override suspend fun sendRanges(items: List<ContextRow>): ApiResult<RangeAnswer> =
        api.request(
            "POST",
            "api/v1/context/ranges",
            body =
                Json.encodeToString(
                    Body(items.map { Item(it.id, it.kind, it.route, instant(it.startMs), instant(it.endMs)) }),
                ),
        ) { api.json.decodeFromString(it) }

    // Instant prints seconds always, as the server's parser wants.
    private fun instant(ms: Long) = Instant.ofEpochMilli(ms).toString()
}
