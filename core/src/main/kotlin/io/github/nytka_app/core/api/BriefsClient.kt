package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

@Serializable
private data class BriefList(
    val items: List<UpcomingBrief> = emptyList(),
)

/**
 * Meeting briefs (server after 0.14); a server without the route answers [FailureKind.NotFound] or
 * [FailureKind.Unsupported].
 */
interface BriefsClient {
    /** The events not over that start within [minutes] (1 to 1440), soonest first, each with its brief or null. */
    suspend fun upcoming(minutes: Int): ApiResult<List<UpcomingBrief>>
}

class BriefsApi(
    private val api: NytkaApi,
) : BriefsClient {
    override suspend fun upcoming(minutes: Int): ApiResult<List<UpcomingBrief>> =
        api.request("GET", "api/v1/briefs/upcoming", mapOf("minutes" to minutes.toString())) {
            api.json.decodeFromString<BriefList>(it).items
        }
}
