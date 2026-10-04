package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The wearer's marks on segments, against the contract of server 0.12. */
class SegmentMarksApiTest {
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

    private fun segmentJson(extra: String = "") =
        """{"id":7,"startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:00:04Z","text":"Hi."$extra}"""

    private suspend fun sentBody(isUser: Boolean?): String {
        answer(200, segmentJson(""","isUser":${isUser ?: "null"},"isUserSource":"manual""""))
        api.markSegment(7, isUser)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/segments/7", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        return request.body!!.utf8()
    }

    @Test
    fun `this is me sends true`() = runTest { assertEquals("""{"isUser":true}""", sentBody(true)) }

    @Test
    fun `this is not me sends false`() = runTest { assertEquals("""{"isUser":false}""", sentBody(false)) }

    @Test
    fun `clear sends null`() = runTest { assertEquals("""{"isUser":null}""", sentBody(null)) }

    @Test
    fun `the answer is the updated segment`() =
        runTest {
            answer(200, segmentJson(""","isUser":true,"isUserSource":"manual","speaker":"SPEAKER_0""""))

            val segment = (api.markSegment(7, true) as ApiResult.Ok).value

            assertEquals(true, segment.isUser)
            assertEquals("manual", segment.isUserSource)
        }

    @Test
    fun `a read token is forbidden`() =
        runTest {
            answer(403)

            assertEquals(FailureKind.Forbidden, (api.markSegment(7, true) as ApiResult.Failure).kind)
        }

    @Test
    fun `an older server or a missing segment is not found`() =
        runTest {
            answer(404)

            assertEquals(FailureKind.NotFound, (api.markSegment(7, null) as ApiResult.Failure).kind)
        }

    @Test
    fun `a server error is a failure`() =
        runTest {
            answer(500)

            assertEquals(FailureKind.Server, (api.markSegment(7, false) as ApiResult.Failure).kind)
        }

    @Test
    fun `a segment decodes isUserSource, and a server before 0_12 sends none`() =
        runTest {
            answer(
                200,
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z","status":"closed",
                "segments":[{"id":1,"startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:00:04Z","text":"A",
                  "isUser":true,"isUserSource":"voice","future":1},
                  {"id":2,"startedAt":"2026-09-29T08:00:05Z","endedAt":"2026-09-29T08:00:08Z","text":"B",
                  "isUser":true},
                  {"id":3,"startedAt":"2026-09-29T08:00:09Z","endedAt":"2026-09-29T08:00:12Z","text":"C",
                  "isUser":null,"isUserSource":null}]}""",
            )

            val segments = (api.conversation("a") as ApiResult.Ok).value.segments

            assertEquals("voice", segments[0].isUserSource)
            assertNull(segments[1].isUserSource)
            assertNull(segments[2].isUserSource)
            assertEquals(true, segments[1].isUser)
        }
}
