package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.BookmarksClient

class FakeBookmarks : BookmarksClient {
    val notes = mutableListOf<Pair<String, String?>>()
    var answer: ApiResult<Unit> = ApiResult.Ok(Unit)

    override suspend fun createBookmark(
        id: String,
        atMs: Long,
        source: String,
    ): ApiResult<Unit> = answer

    override suspend fun setBookmarkNote(
        id: String,
        note: String?,
    ): ApiResult<Unit> = answer.also { notes += id to note }
}
