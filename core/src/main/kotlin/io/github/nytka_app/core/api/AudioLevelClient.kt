package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** How loud the wearer's speech was on a day against the 14 days before (`GET /audio/level`). */
@Serializable
data class DayLevel(
    val day: String,
    val wearerMedianDbfs: Double,
    val baselineMedianDbfs: Double,
    /** The share of the day's speech under −45 dBFS, 0 to 1. */
    val quietShare: Double,
    /** The server's verdict: 10 dB or more under the usual median, or over a quarter of the speech very quiet. */
    val quiet: Boolean,
)

/** The quiet-day signal of a server that lists `audio-level`; a day with no wearer speech answers [FailureKind.NotFound]. */
interface AudioLevelClient {
    suspend fun level(day: LocalDate): ApiResult<DayLevel>
}

class AudioLevelApi(
    private val api: NytkaApi,
) : AudioLevelClient {
    override suspend fun level(day: LocalDate): ApiResult<DayLevel> =
        api.request("GET", "api/v1/audio/level", mapOf("day" to day.toString())) { api.json.decodeFromString(it) }
}
