package io.github.nytka_app.core.api

/** Earlier moments about the same thing (`related`). */
interface RelatedClient {
    /**
     * The earlier moments of a conversation or an item: [kind] is "conversation", [LifeItem.KIND_TASK],
     * [LifeItem.KIND_MEMORY] or [LifeItem.KIND_NOTE]; at most [limit], best first.
     */
    suspend fun related(
        kind: String,
        id: String,
        limit: Int = 5,
    ): ApiResult<List<RelatedMoment>>
}

class RelatedApi(
    private val api: NytkaApi,
) : RelatedClient {
    override suspend fun related(
        kind: String,
        id: String,
        limit: Int,
    ): ApiResult<List<RelatedMoment>> =
        api.request("GET", "api/v1/related", mapOf("${kind}Id" to id, "limit" to limit.toString())) {
            api.json.decodeFromString<RelatedList>(it).items
        }
}

/** Topic pages (`topics`). */
interface TopicsClient {
    /** Every topic, or those of [kind] ("tag" or "person") when given. */
    suspend fun topics(kind: String? = null): ApiResult<List<Topic>>

    /** One topic; [before] continues its moments from a page's `nextBefore`. */
    suspend fun topic(
        id: String,
        before: String? = null,
    ): ApiResult<TopicPage>
}

class TopicsApi(
    private val api: NytkaApi,
) : TopicsClient {
    override suspend fun topics(kind: String?): ApiResult<List<Topic>> =
        api.request("GET", "api/v1/topics", mapOf("kind" to kind)) { api.json.decodeFromString<TopicList>(it).items }

    override suspend fun topic(
        id: String,
        before: String?,
    ): ApiResult<TopicPage> =
        api.request("GET", "api/v1/topics/$id", mapOf("before" to before)) { api.json.decodeFromString(it) }
}

/** Summaries over time (`rollups`). */
interface RollupsClient {
    /** The summaries of [period] ([Rollup.PERIODS]), newest first; [before] (a start date) pages back. */
    suspend fun rollups(
        period: String,
        before: String? = null,
        limit: Int = 1,
    ): ApiResult<List<Rollup>>
}

class RollupsApi(
    private val api: NytkaApi,
) : RollupsClient {
    override suspend fun rollups(
        period: String,
        before: String?,
        limit: Int,
    ): ApiResult<List<Rollup>> =
        api.request(
            "GET",
            "api/v1/rollups",
            mapOf("period" to period, "before" to before, "limit" to limit.toString()),
        ) { api.json.decodeFromString<RollupPage>(it).items }
}
