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

/** The speech-kind marks, against the contract of the server's `speech-kind` feature. */
class SpeechApiTest {
    private val server = MockWebServer()
    private val api =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val speech = SpeechApi(api)

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

    private suspend fun sentSegmentBody(kind: String?): String {
        answer(200, segmentJson(""","speechKind":${kind?.let { "\"$it\"" } ?: "null"},"speechMarked":true"""))
        speech.markSegment(7, kind)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/segments/7", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        return request.body!!.utf8()
    }

    @Test
    fun `a segment mark sends the kind`() =
        runTest { assertEquals("""{"speechKind":"media"}""", sentSegmentBody("media")) }

    @Test
    fun `clearing a segment sends null, not nothing`() =
        runTest { assertEquals("""{"speechKind":null}""", sentSegmentBody(null)) }

    @Test
    fun `the answer to a segment mark is the updated segment`() =
        runTest {
            answer(
                200,
                segmentJson(
                    ""","speechKind":"call","speechGuess":"media","speechScore":0.9,""" +
                        """"speechSignals":["a","b"],"speechMarked":true""",
                ),
            )

            val segment = (speech.markSegment(7, "call") as ApiResult.Ok).value

            assertEquals("call", segment.speechKind)
            assertEquals("media", segment.speechGuess)
            assertEquals(0.9, segment.speechScore!!, 0.0)
            assertEquals(listOf("a", "b"), segment.speechSignals)
            assertEquals(true, segment.speechMarked)
        }

    @Test
    fun `a conversation mark posts the kind and answers the count`() =
        runTest {
            answer(200, """{"marked":4}""")

            val result = speech.markConversation("c1", "person")

            assertEquals(4, (result as ApiResult.Ok).value)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/conversations/c1/speech", request.url.encodedPath)
            assertEquals("""{"kind":"person"}""", request.body!!.utf8())
        }

    @Test
    fun `clearing a conversation sends null`() =
        runTest {
            answer(200, """{"marked":0}""")

            speech.markConversation("c1", null)

            assertEquals("""{"kind":null}""", server.takeRequest().body!!.utf8())
        }

    @Test
    fun `400, 403 and 404 map to their kinds`() =
        runTest {
            val bad = """{"errors":{"kind":["Must be person, media, call or null."]}}"""
            answer(400, bad)
            answer(403)
            answer(404)
            answer(400, bad)
            answer(403)
            answer(404)

            assertEquals(FailureKind.Invalid, (speech.markSegment(7, "x") as ApiResult.Failure).kind)
            assertEquals(FailureKind.Forbidden, (speech.markSegment(7, "media") as ApiResult.Failure).kind)
            assertEquals(FailureKind.NotFound, (speech.markSegment(7, "media") as ApiResult.Failure).kind)
            assertEquals(FailureKind.Invalid, (speech.markConversation("c1", "x") as ApiResult.Failure).kind)
            assertEquals(FailureKind.Forbidden, (speech.markConversation("c1", "media") as ApiResult.Failure).kind)
            assertEquals(FailureKind.NotFound, (speech.markConversation("c1", "media") as ApiResult.Failure).kind)
        }

    @Test
    fun `a segment and a summary without the new fields decode to the defaults`() =
        runTest {
            answer(
                200,
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z","status":"closed",
                "segments":[{"id":1,"startedAt":"2026-09-29T08:00:00Z",
                "endedAt":"2026-09-29T08:00:04Z","text":"A"}]}""",
            )
            answer(
                200,
                """{"items":[{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z",
                "status":"closed","preview":"p"}]}""",
            )

            val segment = (api.conversation("a") as ApiResult.Ok).value.segments.single()
            val summary = (api.conversations(null, 20) as ApiResult.Ok).value.items.single()

            assertNull(segment.speechKind)
            assertNull(segment.speechGuess)
            assertNull(segment.speechScore)
            assertEquals(emptyList<String>(), segment.speechSignals)
            assertFalse(segment.speechMarked)
            assertEquals(0.0, summary.mediaShare, 0.0)
        }

    @Test
    fun `the new fields decode and unknown ones are ignored`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z",
                "status":"closed","preview":"p","mediaShare":0.85,"future":1}]}""",
            )

            assertEquals(
                0.85,
                (api.conversations(null, 20) as ApiResult.Ok)
                    .value.items
                    .single()
                    .mediaShare,
                0.0,
            )
        }
}
