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

    /**
     * Adds a task with no conversation (`task-create`); [due] is a bare date `YYYY-MM-DD` or null.
     * A server without the feature answers [FailureKind.NotFound] or [FailureKind.Unsupported].
     */
    suspend fun create(
        text: String,
        due: String?,
    ): ApiResult<NytkaTask>

    /** Sets the due date to a bare date `YYYY-MM-DD`; null clears it. */
    suspend fun setDue(
        id: String,
        due: String?,
    ): ApiResult<NytkaTask>
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

    override suspend fun create(
        text: String,
        due: String?,
    ): ApiResult<NytkaTask> =
        api.request(
            "POST",
            "api/v1/tasks",
            body =
                buildJsonObject {
                    put("text", text)
                    due?.let { put("due", it) }
                }.toString(),
        ) { api.json.decodeFromString(it) }

    override suspend fun setDue(
        id: String,
        due: String?,
    ): ApiResult<NytkaTask> = patch(id, buildJsonObject { put("due", due) }.toString())

    private suspend fun patch(
        id: String,
        body: String,
    ): ApiResult<NytkaTask> = api.request("PATCH", "api/v1/tasks/$id", body = body) { api.json.decodeFromString(it) }
}
