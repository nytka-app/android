package io.github.nytka_app.core.api

import io.github.nytka_app.core.queue.ContextRow
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The ranges call against the contract of the server's `context-ranges` feature. */
class ContextApiTest {
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

    private val rows =
        listOf(
            ContextRow("a", "media", "speaker", 1_790_000_000_000, 1_790_000_010_000),
            ContextRow("b", "call", "bluetooth", 1_790_000_020_000, 1_790_000_080_500),
        )

    @Test
    fun `sends the items with ISO times and reads the counts`() =
        runTest {
            answer(200, """{"accepted":1,"skipped":1}""")

            val result = ContextApi(api).sendRanges(rows)

            assertEquals(ApiResult.Ok(RangeAnswer(1, 1)), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/context/ranges", request.url.encodedPath)
            assertEquals(
                """{"items":[{"id":"a","kind":"media","route":"speaker","startedAt":"2026-09-21T14:13:20Z",""" +
                    """"endedAt":"2026-09-21T14:13:30Z"},{"id":"b","kind":"call","route":"bluetooth",""" +
                    """"startedAt":"2026-09-21T14:13:40Z","endedAt":"2026-09-21T14:14:40.500Z"}]}""",
                request.body?.utf8(),
            )
        }

    @Test
    fun `a 400 is invalid and 404 and 405 are refusals the uploader pauses on`() =
        runTest {
            answer(400, """{"errors":{"items":["bad"]}}""")
            answer(404)
            answer(405)

            val kinds = List(3) { (ContextApi(api).sendRanges(rows) as ApiResult.Failure).kind }

            assertEquals(FailureKind.Invalid, kinds[0])
            assertEquals(FailureKind.NotFound, kinds[1])
            assertEquals(FailureKind.Unsupported, kinds[2])
        }
}
