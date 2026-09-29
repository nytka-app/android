package io.github.nytka_app.ui.tasks

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.TaskPage
import io.github.nytka_app.core.api.TasksClient

/** Holds the tasks as a server would: newest id first, paged by id, changed by the calls. */
class FakeTasks(
    tasks: List<NytkaTask> = emptyList(),
) : TasksClient {
    val all = tasks.toMutableList()
    var failure: ApiResult.Failure? = null
    val calls = mutableListOf<String>()

    override suspend fun tasks(
        done: Boolean,
        before: String?,
        limit: Int,
    ): ApiResult<TaskPage> {
        calls += "list ${if (done) "done" else "open"} before=$before limit=$limit"
        failure?.let { return it }
        val matching =
            all.filter { it.done == done }.sortedByDescending { it.id }.filter { before == null || it.id < before }
        val page = matching.take(limit)
        return ApiResult.Ok(TaskPage(page, nextBefore = if (matching.size > limit) page.last().id else null))
    }

    override suspend fun setDone(
        id: String,
        done: Boolean,
    ): ApiResult<NytkaTask> {
        calls += "done $id $done"
        return change(id) { it.copy(done = done) }
    }

    override suspend fun editText(
        id: String,
        text: String,
    ): ApiResult<NytkaTask> {
        calls += "edit $id $text"
        return change(id) { it.copy(text = text) }
    }

    override suspend fun deleteTask(id: String): ApiResult<Unit> {
        calls += "delete $id"
        failure?.let { return it }
        return if (all.removeAll { it.id == id }) ApiResult.Ok(Unit) else notFound()
    }

    private fun change(
        id: String,
        edit: (NytkaTask) -> NytkaTask,
    ): ApiResult<NytkaTask> {
        failure?.let { return it }
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        all[index] = edit(all[index])
        return ApiResult.Ok(all[index])
    }

    private fun notFound() = ApiResult.Failure(FailureKind.NotFound, "Not found.")

    companion object {
        fun task(
            id: String,
            text: String = "task $id",
            done: Boolean = false,
            conversationId: String = "c1",
            title: String? = "Standup",
            startedAt: String? = "2026-09-29T08:00:00Z",
        ) = NytkaTask(id, conversationId, text, done, title, startedAt)
    }
}
