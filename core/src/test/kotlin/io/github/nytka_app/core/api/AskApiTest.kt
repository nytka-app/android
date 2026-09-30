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

/** The ask call against the contract of the v0.8 server. */
class AskApiTest {
    private val server = MockWebServer()
    private val api =
        AskApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) })

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
    fun `ask posts the question and decodes the answer with its sources`() =
        runTest {
            answer(
                200,
                """{"answer":"You met Anna [1].","extra":1,"sources":[{"n":1,"kind":"conversation","id":"c1",
                "conversationId":"c1","title":"Coffee","at":"2026-09-28T10:00:00Z","snippet":"met Anna"},
                {"n":2,"kind":"memory","id":"m1","conversationId":null,"title":null,"at":"2026-09-27T10:00:00Z",
                "snippet":"Anna likes tea"}]}""",
            )

            val result = (api.ask("Who did I meet?") as ApiResult.Ok).value

            assertEquals("You met Anna [1].", result.answer)
            assertEquals(
                AskSource(1, "conversation", "c1", "c1", "Coffee", "2026-09-28T10:00:00Z", "met Anna"),
                result.sources[0],
            )
            assertEquals(null, result.sources[1].conversationId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/ask", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertEquals("""{"question":"Who did I meet?"}""", request.body?.utf8())
        }

    @Test
    fun `a 503 is unavailable and a 504 is a timeout, with fixed sentences`() =
        runTest {
            answer(503, "no model configured: secret detail")
            answer(504, "model timed out")

            val unavailable = api.ask("q") as ApiResult.Failure
            val timeout = api.ask("q") as ApiResult.Failure

            assertEquals(FailureKind.Unavailable, unavailable.kind)
            assertEquals("The server answered 503.", unavailable.message)
            assertEquals(FailureKind.Timeout, timeout.kind)
            assertEquals("The server answered 504.", timeout.message)
        }

    @Test
    fun `a 404 from an older server is not found`() =
        runTest {
            answer(404)

            assertEquals(FailureKind.NotFound, (api.ask("q") as ApiResult.Failure).kind)
        }

    @Test
    fun `a dead server is a network failure`() =
        runTest {
            server.close()

            assertEquals(FailureKind.Network, (api.ask("q") as ApiResult.Failure).kind)
        }
}
