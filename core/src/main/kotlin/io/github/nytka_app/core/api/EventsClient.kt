package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A day's derived events (`events`); a server without the route answers [FailureKind.NotFound]. */
interface EventsClient {
    /** The events of [date] (yyyy-MM-dd) in [zone], an IANA zone id; oldest first. */
    suspend fun events(
        date: String,
        zone: String,
    ): ApiResult<DayEvents>
}

class EventsApi(
    private val api: NytkaApi,
) : EventsClient {
    override suspend fun events(
        date: String,
        zone: String,
    ): ApiResult<DayEvents> =
        api.request("GET", "api/v1/events", mapOf("date" to date, "tz" to zone)) { api.json.decodeFromString(it) }
}

/** `GET /events`: the events of [date], oldest first. */
@Serializable
data class DayEvents(
    val date: String,
    val events: List<DayEvent> = emptyList(),
)

/**
 * One thing that happened in the day. [voiceLabel] names the person or the numbered voice of a join or leave.
 * [detail] is the server's small object: `category` on media and calls, `minutes` on silence; it may be absent or of
 * another shape.
 */
@Serializable
data class DayEvent(
    val id: String,
    val kind: String,
    val at: String,
    val conversationId: String? = null,
    val personId: String? = null,
    val voiceLabel: String? = null,
    val detail: JsonElement? = null,
    val source: String? = null,
) {
    val category: String? get() = field("category")?.contentOrNull?.takeIf { it.isNotBlank() }

    val minutes: Int? get() = field("minutes")?.intOrNull

    private fun field(name: String): JsonPrimitive? {
        val value = detail ?: return null
        val obj =
            when (value) {
                is JsonObject -> value
                is JsonPrimitive -> runCatching { Json.parseToJsonElement(value.content) as? JsonObject }.getOrNull()
                else -> null
            }
        return obj?.get(name) as? JsonPrimitive
    }

    companion object {
        const val PERSON_JOINED = "person_joined"
        const val PERSON_LEFT = "person_left"
        const val MEDIA_STARTED = "media_started"
        const val MEDIA_STOPPED = "media_stopped"
        const val CALL_STARTED = "call_started"
        const val CALL_ENDED = "call_ended"
        const val SILENCE = "silence"
        const val ALONE = "alone"
        val KINDS =
            setOf(
                PERSON_JOINED,
                PERSON_LEFT,
                MEDIA_STARTED,
                MEDIA_STOPPED,
                CALL_STARTED,
                CALL_ENDED,
                SILENCE,
                ALONE,
            )
    }
}
