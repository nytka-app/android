package io.github.nytka_app.core.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The Tasks API, apart so the screens can be tested without HTTP. A v0.1 server answers [FailureKind.NotFound]. */
interface TasksClient {
    /** [done] picks the list: open tasks (`false`) or completed ones; [before] is a task id, the cursor of a page. */
    suspend fun tasks(
        done: Boolean,
        before: String?,
        limit: Int,
    ): ApiResult<TaskPage>

    suspend fun setDone(
        id: String,
        done: Boolean,
    ): ApiResult<NytkaTask>

    suspend fun editText(
        id: String,
        text: String,
    ): ApiResult<NytkaTask>

    suspend fun deleteTask(id: String): ApiResult<Unit>
}

class TasksApi(
    private val api: NytkaApi,
) : TasksClient {
    override suspend fun tasks(
        done: Boolean,
        before: String?,
        limit: Int,
    ): ApiResult<TaskPage> =
        api.request(
            "GET",
            "api/v1/tasks",
            mapOf("status" to if (done) "done" else "open", "before" to before, "limit" to limit.toString()),
        ) { api.json.decodeFromString(it) }

    override suspend fun setDone(
        id: String,
        done: Boolean,
    ): ApiResult<NytkaTask> = patch(id, buildJsonObject { put("done", done) }.toString())

    override suspend fun editText(
        id: String,
        text: String,
    ): ApiResult<NytkaTask> = patch(id, buildJsonObject { put("text", text) }.toString())

    override suspend fun deleteTask(id: String): ApiResult<Unit> = api.request("DELETE", "api/v1/tasks/$id") { }

    private suspend fun patch(
        id: String,
        body: String,
    ): ApiResult<NytkaTask> = api.request("PATCH", "api/v1/tasks/$id", body = body) { api.json.decodeFromString(it) }
}
