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

/** Task create, due dates and the promise ledger against the JSON of the server's two specs of 0.20. */
class TaskCreateLedgerApiTest {
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

    private fun <T> ok(result: ApiResult<T>): T = (result as ApiResult.Ok).value

    @Test
    fun `a task by hand posts text and due and decodes a null conversation`() =
        runTest {
            answer(
                201,
                """{"id":"t1","conversationId":null,"conversationTitle":null,"conversationStartedAt":null,
                "text":"Pay rent","kind":"commitment","done":false,"due":"2026-10-24T00:00:00Z"}""",
            )

            val task = ok(TasksApi(api).create("Pay rent", "2026-10-24"))

            assertNull(task.conversationId)
            assertEquals("2026-10-24T00:00:00Z", task.due)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/tasks", request.url.encodedPath)
            assertEquals("""{"text":"Pay rent","due":"2026-10-24"}""", request.body?.utf8())
        }

    @Test
    fun `a task without a date sends no due and a server without the route answers not found`() =
        runTest {
            answer(405)

            val failure = TasksApi(api).create("Pay rent", null) as ApiResult.Failure

            assertEquals(FailureKind.Unsupported, failure.kind)
            assertEquals("""{"text":"Pay rent"}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun `the due date is set by patch and cleared with null`() =
        runTest {
            val task = """{"id":"t1","conversationId":"c1","text":"a","due":null}"""
            answer(200, task)
            answer(200, task)

            TasksApi(api).setDue("t1", "2026-11-01")
            TasksApi(api).setDue("t1", null)

            assertEquals("""{"due":"2026-11-01"}""", server.takeRequest().body?.utf8())
            assertEquals("""{"due":null}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun `the ledger reads both sides open and done`() =
        runTest {
            val entry =
                """{"id":"t1","conversationId":"c1","conversationTitle":"Market","conversationStartedAt":"2026-10-01T08:00:00Z",
                "text":"Bring jars","kind":"commitment","done":false,"doneAt":null,"createdAt":"2026-10-01T09:00:00Z",
                "personId":"p1","personName":"Anna","due":null}"""
            answer(
                200,
                """{"personId":"p1","personName":"Anna","owedByMe":{"open":[$entry],"done":[]},
                "owedToMe":{"open":[],"done":[{"id":"t2","conversationId":null,"text":"Sent it","done":true}]}}""",
            )

            val ledger = ok(LedgerApi(api).ledger("p1"))

            assertEquals("Anna", ledger.personName)
            assertEquals(
                "Bring jars",
                ledger.owedByMe.open
                    .single()
                    .text,
            )
            assertEquals(
                "Sent it",
                ledger.owedToMe.done
                    .single()
                    .text,
            )
            assertNull(
                ledger.owedToMe.done
                    .single()
                    .conversationId,
            )
            assertEquals("/api/v1/people/p1/ledger", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `a server without the ledger answers not found`() =
        runTest {
            answer(404)

            assertEquals(FailureKind.NotFound, (LedgerApi(api).ledger("p1") as ApiResult.Failure).kind)
        }
}
