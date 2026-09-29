package io.github.nytka_app.core.api

import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NytkaApiTest {
    private val server = MockWebServer()
    private var settings = Settings()
    private val api = NytkaApi(OkHttpClient()) { settings }

    @Before
    fun start() {
        server.start()
        settings = Settings(serverUrl = server.url("/").toString(), token = "token-1", privateNetwork = true)
    }

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
    fun `upload sends the chunk with its media type and token`() =
        runTest {
            answer(202, """{"acceptedThroughSeq":9}""")

            val result = api.upload(byteArrayOf(1, 2, 3))

            assertEquals(UploadResult.Accepted(9, duplicate = false), result)
            val request = server.takeRequest()
            assertEquals("/api/v1/chunks", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertEquals(ChunkFormat.MEDIA_TYPE, request.headers["Content-Type"])
            assertEquals(3L, request.body?.size?.toLong())
        }

    @Test
    fun `upload maps every answer`() =
        runTest {
            answer(200, """{"acceptedThroughSeq":4}""")
            answer(409)
            answer(400)
            answer(413)
            answer(401)
            answer(500)

            val results = List(6) { api.upload(byteArrayOf(1)) }

            assertEquals(UploadResult.Accepted(4, duplicate = true), results[0])
            assertEquals(listOf(409, 400, 413), results.subList(1, 4).map { (it as UploadResult.Dropped).code })
            assertEquals(UploadResult.Unauthorized, results[4])
            assertTrue(results[5] is UploadResult.Retry)
        }

    @Test
    fun `a dead server is a retry`() =
        runTest {
            server.close()

            assertTrue(api.upload(byteArrayOf(1)) is UploadResult.Retry)
        }

    @Test
    fun `plain http without the switch never leaves the phone`() =
        runTest {
            settings = settings.copy(privateNetwork = false)

            val result = api.upload(byteArrayOf(1))

            assertTrue(result is UploadResult.NotConfigured)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `reads a conversation page`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z",
               "status":"closed","preview":"hello"}],"nextBefore":"2026-09-29T08:00:00Z"}""",
            )

            val page = (api.conversations(before = "2026-09-29T09:00:00Z", limit = 1) as ApiResult.Ok).value

            assertEquals("hello", page.items.single().preview)
            assertEquals("2026-09-29T08:00:00Z", page.nextBefore)
            val request = server.takeRequest()
            assertEquals("1", request.url.queryParameter("limit"))
            assertEquals("2026-09-29T09:00:00Z", request.url.queryParameter("before"))
        }

    @Test
    fun `maps failures to kinds`() =
        runTest {
            answer(404)
            answer(401)
            answer(503)

            assertEquals(FailureKind.NotFound, (api.conversation("x") as ApiResult.Failure).kind)
            assertEquals(FailureKind.Unauthorized, (api.info() as ApiResult.Failure).kind)
            assertEquals(FailureKind.Server, (api.status() as ApiResult.Failure).kind)
        }

    @Test
    fun `delete answers ok on 204`() =
        runTest {
            answer(204)

            assertEquals(ApiResult.Ok(Unit), api.deleteConversation("a"))
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun `diagnostics are posted as json with the token`() =
        runTest {
            answer(200, """{"accepted":2}""")

            val result = api.uploadDiagnostics("""[{"id":"a"},{"id":"b"}]""")

            assertEquals(DiagnosticsResult.Accepted(2), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/diagnostics", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
            assertEquals("""[{"id":"a"},{"id":"b"}]""", request.body?.utf8())
        }

    @Test
    fun `diagnostics map every answer`() =
        runTest {
            answer(404)
            answer(401)
            answer(400)
            answer(413)
            answer(503)
            answer(200, "not json")

            assertEquals(DiagnosticsResult.NotSupported, api.uploadDiagnostics("[]"))
            assertEquals(DiagnosticsResult.Unauthorized, api.uploadDiagnostics("[]"))
            assertEquals(DiagnosticsResult.BadRequest, api.uploadDiagnostics("[]"))
            assertEquals(DiagnosticsResult.TooLarge, api.uploadDiagnostics("[]"))
            assertEquals(DiagnosticsResult.Retry("The server answered 503."), api.uploadDiagnostics("[]"))
            assertTrue(api.uploadDiagnostics("[]") is DiagnosticsResult.Retry)
        }

    @Test
    fun `diagnostics need a token`() =
        runTest {
            settings = settings.copy(token = "")

            assertEquals(DiagnosticsResult.NotConfigured("No token is set."), api.uploadDiagnostics("[]"))
            assertEquals(0, server.requestCount)
        }
}
