package io.github.nytka_app.core.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Today and the Timeline (`today`); a server without the routes answers [FailureKind.NotFound]. */
interface TodayClient {
    /** Today's vital items in [zone], an IANA zone id such as "Europe/Kyiv". */
    suspend fun today(zone: String): ApiResult<TodayPage>

    /** The scenes of [date] (yyyy-MM-dd) in [zone]. */
    suspend fun timeline(
        date: String,
        zone: String,
    ): ApiResult<Timeline>
}

class TodayApi(
    private val api: NytkaApi,
) : TodayClient {
    override suspend fun today(zone: String): ApiResult<TodayPage> =
        api.request("GET", "api/v1/today", mapOf("tz" to zone)) { api.json.decodeFromString(it) }

    override suspend fun timeline(
        date: String,
        zone: String,
    ): ApiResult<Timeline> =
        api.request("GET", "api/v1/timeline", mapOf("date" to date, "tz" to zone)) { api.json.decodeFromString(it) }
}

/** Patterns and reports over time (`insights`). */
interface InsightsClient {
    suspend fun insights(): ApiResult<Insights>
}

class InsightsApi(
    private val api: NytkaApi,
) : InsightsClient {
    override suspend fun insights(): ApiResult<Insights> =
        api.request("GET", "api/v1/insights") { api.json.decodeFromString(it) }
}

/**
 * The wearer's corrections on an item and the lines it cites, through the server's existing manual marks: a line is
 * not the wearer's, a line is someone else's, an item is not important. Needs an admin token.
 */
interface ItemMarksClient {
    /** Sets the wearer's own [salience] on the item of [kind]; the server keeps it over the model's. */
    suspend fun setSalience(
        kind: String,
        id: String,
        salience: String?,
    ): ApiResult<Unit>

    /** `true` the line is the wearer's, `false` it is not, `null` clears the mark. */
    suspend fun markLine(
        segmentId: Long,
        isUser: Boolean?,
    ): ApiResult<Unit>

    /** Says whose the line is; `null` takes the person off it. */
    suspend fun setLinePerson(
        segmentId: Long,
        personId: String?,
    ): ApiResult<Unit>
}

class ItemMarksApi(
    private val api: NytkaApi,
) : ItemMarksClient {
    override suspend fun setSalience(
        kind: String,
        id: String,
        salience: String?,
    ): ApiResult<Unit> =
        api.request(
            "PATCH",
            "api/v1/${pathOf(kind)}/$id",
            body = buildJsonObject { put("salience", salience?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { }

    override suspend fun markLine(
        segmentId: Long,
        isUser: Boolean?,
    ): ApiResult<Unit> =
        api.request(
            "PATCH",
            "api/v1/segments/$segmentId",
            body = buildJsonObject { put("isUser", isUser?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { }

    override suspend fun setLinePerson(
        segmentId: Long,
        personId: String?,
    ): ApiResult<Unit> =
        api.request(
            "PATCH",
            "api/v1/segments/$segmentId",
            body = buildJsonObject { put("personId", personId?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { }

    private fun pathOf(kind: String): String =
        when (kind) {
            LifeItem.KIND_TASK -> "tasks"
            LifeItem.KIND_MEMORY -> "memories"
            LifeItem.KIND_NOTE -> "notes"
            else -> throw IllegalArgumentException("No such kind of item: $kind")
        }
}
