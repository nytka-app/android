package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The bookmark calls against the contract of the v0.8 server. */
class BookmarksApiTest {
    private val server = MockWebServer()
    private val api =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun answer(
        code: Int,
        body: String = "",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun `setting a note sends a PATCH with the note`() =
        runTest {
            answer(200, "{}")

            val result = BookmarksApi(api).setBookmarkNote("b1", "Call Anna")

            assertEquals(ApiResult.Ok(Unit), result)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/bookmarks/b1", request.url.encodedPath)
            assertEquals("""{"note":"Call Anna"}""", request.body?.utf8())
        }

    @Test
    fun `clearing a note sends null`() =
        runTest {
            answer(200, "{}")

            BookmarksApi(api).setBookmarkNote("b1", null)

            assertEquals("""{"note":null}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun `a conversation from a server before v0_8 has no bookmarks`() {
        val detail =
            api.json.decodeFromString<ConversationDetail>(
                """{"id":"c","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:10:00Z","status":"closed",
                "segments":[]}""",
            )
        val item =
            api.json.decodeFromString<ConversationSummary>(
                """{"id":"c","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:10:00Z","status":"closed",
                "preview":"p"}""",
            )

        assertEquals(emptyList<Bookmark>(), detail.bookmarks)
        assertEquals(0, item.bookmarks)
    }

    @Test
    fun `a v0_8 conversation carries its bookmarks and the list a count`() {
        val detail =
            api.json.decodeFromString<ConversationDetail>(
                """{"id":"c","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:10:00Z","status":"closed",
                "segments":[],"bookmarks":[{"id":"b1","at":"2026-09-29T08:03:00Z","note":null},
                {"id":"b2","at":"2026-09-29T08:04:00Z","note":"Idea"}]}""",
            )
        val item =
            api.json.decodeFromString<ConversationSummary>(
                """{"id":"c","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:10:00Z","status":"closed",
                "preview":"p","bookmarks":2}""",
            )

        assertEquals(
            listOf(Bookmark("b1", "2026-09-29T08:03:00Z"), Bookmark("b2", "2026-09-29T08:04:00Z", "Idea")),
            detail.bookmarks,
        )
        assertEquals(2, item.bookmarks)
    }
}
