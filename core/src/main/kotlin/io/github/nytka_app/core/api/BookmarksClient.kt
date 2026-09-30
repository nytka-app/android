package io.github.nytka_app.core.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** The bookmark endpoints of v0.8; a server before them answers [FailureKind.NotFound] or [FailureKind.Unsupported]. */
interface BookmarksClient {
    /** Both 201 (new) and 200 (the id exists) are [ApiResult.Ok]: a retry after a lost answer is a no-op. */
    suspend fun createBookmark(
        id: String,
        atMs: Long,
        source: String,
    ): ApiResult<Unit>

    /** [note] null clears it; the server allows 200 characters. */
    suspend fun setBookmarkNote(
        id: String,
        note: String?,
    ): ApiResult<Unit>
}

class BookmarksApi(
    private val api: NytkaApi,
) : BookmarksClient {
    override suspend fun createBookmark(
        id: String,
        atMs: Long,
        source: String,
    ): ApiResult<Unit> =
        api.request(
            "POST",
            "api/v1/bookmarks",
            body =
                buildJsonObject {
                    put("id", id)
                    put("at", Instant.ofEpochMilli(atMs).toString())
                    put("source", source)
                }.toString(),
        ) { }

    override suspend fun setBookmarkNote(
        id: String,
        note: String?,
    ): ApiResult<Unit> =
        api.request(
            "PATCH",
            "api/v1/bookmarks/$id",
            body = buildJsonObject { put("note", note?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { }
}
