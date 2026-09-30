package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The audio index call against the contract of the v0.8 server, and the mapping from capture time to position. */
class AudioApiTest {
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
    fun `the index is read with the bearer token`() =
        runTest {
            answer(
                200,
                """{"durationMs":90000,"runs":[{"offsetMs":0,"startedAt":"2026-09-29T08:00:00Z",""" +
                    """"endedAt":"2026-09-29T08:01:00Z"},{"offsetMs":60000,"startedAt":"2026-09-29T08:05:00Z",""" +
                    """"endedAt":"2026-09-29T08:05:30Z","extra":1}]}""",
            )

            val result = AudioApi(api).audioIndex("c1")

            assertEquals(
                ApiResult.Ok(
                    AudioIndex(
                        90_000,
                        listOf(
                            AudioRun(0, "2026-09-29T08:00:00Z", "2026-09-29T08:01:00Z"),
                            AudioRun(60_000, "2026-09-29T08:05:00Z", "2026-09-29T08:05:30Z"),
                        ),
                    ),
                ),
                result,
            )
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/conversations/c1/audio/index", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
        }

    @Test
    fun `a conversation without audio is not found`() =
        runTest {
            answer(404)

            val result = AudioApi(api).audioIndex("c1")

            assertEquals(FailureKind.NotFound, (result as ApiResult.Failure).kind)
        }

    @Test
    fun `a read token is not refused`() =
        runTest {
            answer(403)

            assertEquals(FailureKind.Forbidden, (AudioApi(api).audioIndex("c1") as ApiResult.Failure).kind)
        }

    @Test
    fun `the stream request carries the url and the header, and hides the token`() =
        runTest {
            val request = AudioApi(api).audioRequest("c1")!!

            assertEquals(server.url("/api/v1/conversations/c1/audio").toString(), request.url)
            assertEquals("Bearer token-1", request.authorization)
            assertFalse(request.toString().contains("token-1"))
        }

    @Test
    fun `no stream request without a token`() =
        runTest {
            val unset = AudioApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "") })

            assertNull(unset.audioRequest("c1"))
        }

    private val index =
        AudioIndex(
            durationMs = 90_000,
            runs =
                listOf(
                    AudioRun(0, "2026-09-29T08:00:00Z", "2026-09-29T08:01:00Z"),
                    AudioRun(60_000, "2026-09-29T08:05:00Z", "2026-09-29T08:05:30Z"),
                ),
        )

    private fun position(at: String) = index.positionOf(Instant.parse(at))

    @Test
    fun `inside a run the position follows the clock`() {
        assertEquals(15_000, position("2026-09-29T08:00:15Z"))
        assertEquals(60_000 + 10_500, position("2026-09-29T08:05:10.500Z"))
    }

    @Test
    fun `in a gap the position is the start of the next run`() {
        assertEquals(60_000, position("2026-09-29T08:03:00Z"))
    }

    @Test
    fun `before the first run the position is its start`() {
        assertEquals(0, position("2026-09-29T07:59:00Z"))
    }

    @Test
    fun `after the last run the position is the end of the stream`() {
        assertEquals(90_000, position("2026-09-29T09:00:00Z"))
    }

    @Test
    fun `the first instant of a run is its offset and its last instant is the next gap`() {
        assertEquals(0, position("2026-09-29T08:00:00Z"))
        assertEquals(60_000, position("2026-09-29T08:01:00Z"))
    }
}
