package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** The playback map of `GET /conversations/{id}/audio/index`; a server before v0.8 answers [FailureKind.NotFound]. */
@Serializable
data class AudioIndex(
    val durationMs: Long,
    val runs: List<AudioRun> = emptyList(),
) {
    /**
     * Where in the stream the moment [at] is heard. Pauses are not in the stream: a moment inside a run maps to its
     * place in that run, one in a gap (or before the first run) to the start of the next run, and one after the last
     * run to the end of the stream.
     */
    fun positionOf(at: Instant): Long {
        val next = runs.firstOrNull { at < Instant.parse(it.endedAt) } ?: return durationMs
        val start = Instant.parse(next.startedAt)
        return if (at <= start) next.offsetMs else next.offsetMs + Duration.between(start, at).toMillis()
    }
}

/** One stretch of continuous capture and where it starts in the stream. */
@Serializable
data class AudioRun(
    val offsetMs: Long,
    val startedAt: String,
    val endedAt: String,
)

/** Where to fetch the audio and how to authorize it. The header holds the token, so [toString] hides it. */
class AudioRequest(
    val url: String,
    val authorization: String,
) {
    override fun toString() = "AudioRequest(url=$url, authorization=***)"
}

interface AudioClient {
    suspend fun audioIndex(id: String): ApiResult<AudioIndex>

    /** Null when no server address or token is set. The stream itself is fetched by the player, with range requests. */
    suspend fun audioRequest(id: String): AudioRequest?
}

class AudioApi(
    private val api: NytkaApi,
) : AudioClient {
    override suspend fun audioIndex(id: String): ApiResult<AudioIndex> =
        api.request("GET", "api/v1/conversations/$id/audio/index") { api.json.decodeFromString(it) }

    override suspend fun audioRequest(id: String): AudioRequest? = api.authorized("api/v1/conversations/$id/audio")
}
