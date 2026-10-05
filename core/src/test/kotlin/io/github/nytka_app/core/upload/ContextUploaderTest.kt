package io.github.nytka_app.core.upload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ContextApi
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.queue.ContextOutbox
import io.github.nytka_app.core.queue.ContextRow
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
class ContextUploaderTest {
    private val server = MockWebServer()
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val outbox = ContextOutbox(database.contextOutbox())
    private val settings by lazy {
        MutableStateFlow(Settings(server.url("/").toString(), "token-1", privateNetwork = true))
    }
    private val api = NytkaApi(OkHttpClient()) { settings.value }
    private var features = listOf(ServerInfo.FEATURE_CONTEXT_RANGES)
    private var infoCalls = 0
    private val info =
        InfoClient {
            infoCalls++
            ApiResult.Ok(ServerInfo("0.30.0", 1, features = features))
        }
    private var now = NOW
    private val uploader by lazy { ContextUploader(outbox, ContextApi(api), info, settings, { now }) }

    @Before
    fun start() = server.start()

    @After
    fun stop() {
        server.close()
        database.close()
    }

    private fun row(
        id: String,
        startMs: Long = NOW - 60_000,
        endMs: Long = startMs + 10_000,
    ) = ContextRow(id, "media", "speaker", startMs, endMs)

    private fun answer(
        code: Int,
        body: String = """{"accepted":1,"skipped":0}""",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun `a 200 removes the ranges and sends their ids, kind, route and ISO times`() =
        runTest {
            outbox.add(
                ContextRow(
                    "3f2c0000-0000-4000-8000-000000000001",
                    "call",
                    "earpiece",
                    1_790_000_000_000,
                    1_790_000_065_000,
                ),
            )
            now = 1_790_000_100_000
            answer(200)

            assertEquals(DrainResult.Empty, uploader.drain())

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/context/ranges", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertEquals(
                """{"items":[{"id":"3f2c0000-0000-4000-8000-000000000001","kind":"call","route":"earpiece",""" +
                    """"startedAt":"2026-09-21T14:13:20Z","endedAt":"2026-09-21T14:14:25Z"}]}""",
                request.body?.utf8(),
            )
            assertTrue(outbox.oldest(10).isEmpty())
        }

    @Test
    fun `at most 500 go in a request`() =
        runTest {
            (1..501).forEach { outbox.add(row("r%03d".format(it), startMs = NOW - 100_000 + it)) }
            answer(200, """{"accepted":500,"skipped":0}""")
            answer(200)

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(2, server.requestCount)
            assertEquals(
                500,
                server
                    .takeRequest()
                    .body!!
                    .utf8()
                    .split("\"id\"")
                    .size - 1,
            )
            assertEquals(
                1,
                server
                    .takeRequest()
                    .body!!
                    .utf8()
                    .split("\"id\"")
                    .size - 1,
            )
            assertTrue(outbox.oldest(10).isEmpty())
        }

    @Test
    fun `a 400 drops the batch`() =
        runTest {
            outbox.add(row("bad"))
            answer(400, """{"errors":{"startedAt":["bad"]}}""")

            assertEquals(DrainResult.Empty, uploader.drain())

            assertTrue(outbox.oldest(10).isEmpty())
        }

    @Test
    fun `401, 403, 404 and 405 pause and keep the ranges`() =
        runTest {
            listOf(401, 403, 404, 405).forEach { code ->
                outbox.add(row("a$code"))
                answer(code)

                assertTrue("$code", uploader.drain() is DrainResult.Paused)
            }
            assertEquals(4, outbox.oldest(10).size)
        }

    @Test
    fun `a 5xx keeps the ranges and backs off`() =
        runTest {
            outbox.add(row("a"))
            answer(503)
            answer(200)

            assertEquals(DrainResult.Failed(5_000), uploader.drain())
            assertEquals(1, outbox.oldest(10).size)
            assertEquals(DrainResult.Empty, uploader.drain())
            assertTrue(outbox.oldest(10).isEmpty())
        }

    @Test
    fun `nothing is sent without context-ranges in info`() =
        runTest {
            features = emptyList()
            outbox.add(row("a"))

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(0, server.requestCount)
            assertEquals(1, outbox.oldest(10).size)
        }

    @Test
    fun `nothing is sent, not even an info call, with the switch off`() =
        runTest {
            settings.value = settings.value.copy(phoneContext = false)
            outbox.add(row("a"))

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(0, server.requestCount)
            assertEquals(0, infoCalls)
            assertEquals(1, outbox.oldest(10).size)
        }

    @Test
    fun `an info refusal pauses and a network failure backs off`() =
        runTest {
            outbox.add(row("a"))
            val refused =
                ContextUploader(
                    outbox,
                    ContextApi(api),
                    { ApiResult.Failure(FailureKind.Unauthorized, "no") },
                    settings,
                    { now },
                )
            val offline =
                ContextUploader(
                    outbox,
                    ContextApi(api),
                    { ApiResult.Failure(FailureKind.Network, "down") },
                    settings,
                    { now },
                )

            assertTrue(refused.drain() is DrainResult.Paused)
            assertEquals(DrainResult.Failed(5_000), offline.drain())
            assertEquals(1, outbox.oldest(10).size)
        }

    @Test
    fun `ranges that ended over 7 days ago are deleted unsent`() =
        runTest {
            outbox.add(row("old", startMs = NOW - 8L * 24 * 3_600_000))
            outbox.add(row("fresh"))
            answer(200)

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(1, server.requestCount)
            assertTrue(
                server
                    .takeRequest()
                    .body!!
                    .utf8()
                    .let { "fresh" in it && "old" !in it },
            )
        }

    private companion object {
        const val NOW = 1_800_000_000_000
    }
}
