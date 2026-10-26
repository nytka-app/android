package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/** One notification the server wants shown: [id] is stable across polls, [deepLink] a `nytka://` link. */
@Serializable
data class Nudge(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val at: String,
)

@Serializable
private data class NudgeList(
    val items: List<Nudge> = emptyList(),
)

/**
 * Nudges (server with the `nudges` feature); an older server answers [FailureKind.NotFound] or
 * [FailureKind.Unsupported].
 */
interface NudgesClient {
    /** The nudges shown from now minus 24 hours, oldest first; [zone] (an IANA id) sets quiet hours and the day. */
    suspend fun recent(zone: String): ApiResult<List<Nudge>>
}

class NudgesApi(
    private val api: NytkaApi,
) : NudgesClient {
    override suspend fun recent(zone: String): ApiResult<List<Nudge>> =
        api.request("GET", "api/v1/nudges", mapOf("tz" to zone)) {
            api.json.decodeFromString<NudgeList>(it).items
        }
}
