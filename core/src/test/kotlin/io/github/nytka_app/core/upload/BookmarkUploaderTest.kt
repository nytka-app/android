package io.github.nytka_app.core.upload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.api.BookmarksApi
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.queue.BookmarkOutbox
import io.github.nytka_app.core.queue.QueueDatabase
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarkUploaderTest {
    private val server = MockWebServer()
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val outbox = BookmarkOutbox(database.bookmarks())
    private val settings by lazy {
        MutableStateFlow(Settings(server.url("/").toString(), "token-1", privateNetwork = true))
    }
    private val api = NytkaApi(OkHttpClient()) { settings.value }
    private val uploader by lazy { BookmarkUploader(outbox, BookmarksApi(api), settings) }

    @Before
    fun start() = server.start()

    @After
    fun stop() {
        server.close()
        database.close()
    }

    private fun answer(code: Int) =
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .body("{}")
                .build(),
        )

    @Test
    fun `a 201 removes the bookmark and sends its id, time and source`() =
        runTest {
            outbox.add("3f2c0000-0000-4000-8000-000000000001", 1_790_000_000_000, "pendant")
            answer(201)

            assertEquals(DrainResult.Empty, uploader.drain())

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/bookmarks", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertEquals(
                """{"id":"3f2c0000-0000-4000-8000-000000000001","at":"2026-09-21T14:13:20Z","source":"pendant"}""",
                request.body?.utf8(),
            )
            assertEquals(null, outbox.oldest())
        }

    @Test
    fun `a 200 for an id the server has is a no-op that still removes it`() =
        runTest {
            outbox.add("a", 1, "pendant")
            answer(200)

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(null, outbox.oldest())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a 5xx keeps the bookmark and backs off 5 s, then 10 s, and a later 201 clears it`() =
        runTest {
            outbox.add("a", 1, "pendant")
            answer(503)
            answer(500)
            answer(201)

            assertEquals(DrainResult.Failed(5_000), uploader.drain())
            assertEquals(DrainResult.Failed(10_000), uploader.drain())
            assertEquals("a", outbox.oldest()?.id)

            assertEquals(DrainResult.Empty, uploader.drain())
            assertEquals(null, outbox.oldest())
        }

    @Test
    fun `an unreachable server keeps the bookmark and backs off`() =
        runTest {
            outbox.add("a", 1, "pendant")
            server.close()

            assertTrue(uploader.drain() is DrainResult.Failed)
            assertEquals("a", outbox.oldest()?.id)
        }

    @Test
    fun `bookmarks go oldest first, and a 401 pauses without losing any`() =
        runTest {
            outbox.add("late", 20, "pendant")
            outbox.add("early", 10, "pendant")
            answer(201)
            answer(401)

            assertTrue(uploader.drain() is DrainResult.Paused)

            assertEquals("late", outbox.oldest()?.id)
            assertTrue(
                server
                    .takeRequest()
                    .body
                    ?.utf8()!!
                    .contains("early"),
            )
        }

    @Test
    fun `a 400 drops the bookmark so it cannot block the rest`() =
        runTest {
            outbox.add("bad", 1, "pendant")
            outbox.add("good", 2, "pendant")
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body("""{"errors":{"at":["bad"]}}""")
                    .build(),
            )
            answer(201)

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(null, outbox.oldest())
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `adding the same id twice keeps one bookmark`() =
        runTest {
            outbox.add("a", 1, "pendant")
            outbox.add("a", 2, "pendant")
            answer(201)
            answer(201)

            uploader.drain()

            assertEquals(1, server.requestCount)
        }
}
