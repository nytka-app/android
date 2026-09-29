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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The v0.2 calls against the JSON of docs/specs/v0.2.md, and the v0.1 answers they must still read. */
class V02ApiTest {
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
    fun `a v0_1 conversation and info and status decode with defaults`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z",
                "status":"closed","preview":"hello"}],"nextBefore":null}""",
            )
            answer(200, """{"serverVersion":"0.1.0","apiVersion":1}""")
            answer(200, """{"pendingChunks":2,"oldestPendingAt":null,"lastError":null}""")
            answer(
                200,
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z","status":"closed",
                "segments":[{"id":1,"startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:00:03Z",
                  "text":"Hi."}]}""",
            )

            val row = ok(api.conversations(null, 30)).items.single()
            val info = ok(api.info())
            val status = ok(api.status())
            val detail = ok(api.conversation("a"))

            assertNull(row.title)
            assertEquals("none", row.aiStatus)
            assertEquals("admin", info.scope)
            assertNull(status.ai)
            assertNull(detail.summary)
            assertTrue(detail.tasks.isEmpty())
            assertNull(detail.segments.single().speaker)
        }

    @Test
    fun `a v0_2 conversation detail decodes tasks and speakers`() =
        runTest {
            answer(
                200,
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:10:00Z","status":"closed",
                "title":"Launch","summary":"They agreed.","aiStatus":"done","titleEdited":false,"aiMessage":null,
                "aiUpdatedAt":"2026-09-29T08:12:00Z",
                "tasks":[{"id":"t1","conversationId":"a","conversationTitle":"Launch",
                  "conversationStartedAt":"2026-09-29T08:00:00Z","text":"Send the deck","done":false,"doneAt":null,
                  "createdAt":"2026-09-29T08:12:00Z"}],
                "segments":[{"id":1,"startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:00:03Z",
                  "text":"Hi.","speaker":"Anna"}]}""",
            )

            val detail = ok(api.conversation("a"))

            assertEquals("Launch", detail.title)
            assertEquals("done", detail.aiStatus)
            assertEquals("Send the deck", detail.tasks.single().text)
            assertEquals("Anna", detail.segments.single().speaker)
        }

    @Test
    fun `status reads the v0_2 fields`() =
        runTest {
            answer(
                200,
                """{"pendingChunks":0,"oldestPendingAt":null,"lastError":"boom","lastErrorAt":"2026-09-29T08:00:00Z",
                "lastSuccessAt":"2026-09-29T07:00:00Z",
                "ai":{"configured":true,"pending":1,"lastError":null,"lastErrorAt":null}}""",
            )

            val status = ok(api.status())

            assertEquals("boom", status.lastError)
            assertEquals("2026-09-29T08:00:00Z", status.lastErrorAt)
            assertTrue(status.ai!!.configured)
        }

    @Test
    fun `rename sends the title, and null restores the generated one`() =
        runTest {
            val body =
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z","status":"closed",
                "segments":[]}"""
            answer(200, body)
            answer(200, body)

            api.renameConversation("a", "New")
            api.renameConversation("a", null)

            val first = server.takeRequest()
            assertEquals("PATCH", first.method)
            assertEquals("/api/v1/conversations/a", first.url.encodedPath)
            assertEquals("""{"title":"New"}""", first.body?.utf8())
            assertEquals("""{"title":null}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun `a v0_6 segment decodes the voice id, the wearer flag and the person`() =
        runTest {
            answer(
                200,
                """{"id":"a","startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:01:00Z","status":"closed",
                "segments":[{"id":1,"startedAt":"2026-09-29T08:00:00Z","endedAt":"2026-09-29T08:00:04Z",
                "text":"Hi.","speaker":"SPEAKER_4","speakerId":"4","isUser":false,"personId":"p1",
                "personName":"Anna"}]}""",
            )

            val segment = ok(api.conversation("a")).segments.single()

            assertEquals("4", segment.speakerId)
            assertEquals(false, segment.isUser)
            assertEquals("Anna", segment.personName)
        }

    @Test
    fun `naming a voice posts the name and the voice id, and an older server is not found`() =
        runTest {
            answer(201, "{}")
            answer(404)

            assertTrue(api.nameVoice("4", "Anna") is ApiResult.Ok)
            val failure = api.nameVoice("4", "Anna") as ApiResult.Failure

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/people", request.url.encodedPath)
            assertEquals("""{"name":"Anna","speakerId":"4"}""", request.body?.utf8())
            assertEquals(FailureKind.NotFound, failure.kind)
        }

    @Test
    fun `enrich posts and reads 202, and 409 is a conflict`() =
        runTest {
            answer(202, """{"aiStatus":"pending"}""")
            answer(409)

            assertEquals(ApiResult.Ok(Unit), api.enrichConversation("a"))
            assertEquals("/api/v1/conversations/a/enrich", server.takeRequest().url.encodedPath)
            assertEquals(FailureKind.Conflict, (api.enrichConversation("a") as ApiResult.Failure).kind)
        }

    @Test
    fun `tasks are listed by status with the cursor and changed by patch`() =
        runTest {
            val tasks = TasksApi(api)
            val task =
                """{"id":"t1","conversationId":"a","conversationTitle":null,"conversationStartedAt":"2026-09-29T08:00:00Z",
                "text":"Send the deck","done":true,"doneAt":"2026-09-29T09:00:00Z","createdAt":"2026-09-29T08:12:00Z"}"""
            answer(200, """{"items":[$task],"nextBefore":"t1"}""")
            answer(200, task)
            answer(200, task)
            answer(204)

            val page = ok(tasks.tasks(done = true, before = "t9", limit = 30))
            tasks.setDone("t1", true)
            tasks.editText("t1", "Send it")
            assertEquals(ApiResult.Ok(Unit), tasks.deleteTask("t1"))

            assertEquals("t1", page.nextBefore)
            assertTrue(page.items.single().done)
            val list = server.takeRequest()
            assertEquals("done", list.url.queryParameter("status"))
            assertEquals("t9", list.url.queryParameter("before"))
            assertEquals("30", list.url.queryParameter("limit"))
            assertEquals("""{"done":true}""", server.takeRequest().body?.utf8())
            assertEquals("""{"text":"Send it"}""", server.takeRequest().body?.utf8())
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertEquals("/api/v1/tasks/t1", delete.url.encodedPath)
        }

    @Test
    fun `tasks ask for open ones by default and a v0_1 server answers not found`() =
        runTest {
            answer(404)

            val failure = TasksApi(api).tasks(done = false, before = null, limit = 50) as ApiResult.Failure

            assertEquals(FailureKind.NotFound, failure.kind)
            assertEquals("open", server.takeRequest().url.queryParameter("status"))
        }

    @Test
    fun `settings are read and patched as strings, null restoring the default`() =
        runTest {
            val settings = ServerSettingsApi(api)
            val item =
                """{"key":"llm.model","type":"string","value":"gpt-x","isSet":true,"source":"db","locked":false,
                "default":null}"""
            val secret =
                """{"key":"llm.apiKey","type":"secret","value":null,"isSet":true,"source":"env","locked":true,
                "default":null}"""
            answer(200, """{"items":[$item,$secret]}""")
            answer(200, """{"items":[$item]}""")

            val read = ok(settings.settings())
            settings.update(mapOf("llm.model" to "gpt-y", "audio.retentionDays" to null))

            assertEquals(listOf("llm.model", "llm.apiKey"), read.map { it.key })
            assertTrue(read[1].secret)
            assertNull(read[1].value)
            assertFalse(read[0].locked)
            server.takeRequest()
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals(
                """{"values":{"llm.model":"gpt-y","audio.retentionDays":null}}""",
                patch.body?.utf8(),
            )
        }

    @Test
    fun `a bad setting is invalid with its messages and a locked one is a conflict`() =
        runTest {
            val settings = ServerSettingsApi(api)
            answer(400, """{"status":400,"errors":{"audio.retentionDays":["Must be 0 to 3650."]}}""")
            answer(409)

            val invalid = settings.update(mapOf("audio.retentionDays" to "-1")) as ApiResult.Failure
            val conflict = settings.update(mapOf("stt.url" to "https://x")) as ApiResult.Failure

            assertEquals(FailureKind.Invalid, invalid.kind)
            assertEquals(listOf("Must be 0 to 3650."), invalid.errors["audio.retentionDays"])
            assertEquals(FailureKind.Conflict, conflict.kind)
        }

    @Test
    fun `tokens are listed, created with the secret once, and revoked`() =
        runTest {
            val tokens = TokensApi(api)
            answer(
                200,
                """{"items":[{"id":"t1","name":"agent","scope":"read","hint":"ab12","createdAt":"2026-09-29T08:00:00Z",
                "lastUsedAt":null,"revokedAt":null}]}""",
            )
            answer(
                201,
                """{"id":"t2","name":"laptop","scope":"admin","hint":"cret","createdAt":"2026-09-29T09:00:00Z",
                "lastUsedAt":null,"revokedAt":null,"token":"nyt_secret"}""",
            )
            answer(204)

            val list = ok(tokens.tokens())
            val created = ok(tokens.createToken("laptop", "admin"))
            assertEquals(ApiResult.Ok(Unit), tokens.revokeToken("t1"))

            assertEquals("ab12", list.single().hint)
            assertNull(list.single().revokedAt)
            assertEquals("nyt_secret", created.token)
            assertFalse(created.listed().toString().contains("nyt_secret"))
            server.takeRequest()
            assertEquals("""{"name":"laptop","scope":"admin"}""", server.takeRequest().body?.utf8())
            val revoke = server.takeRequest()
            assertEquals("DELETE", revoke.method)
            assertEquals("/api/v1/tokens/t1", revoke.url.encodedPath)
        }

    @Test
    fun `a 405 from a v0_1 server is unsupported, not a missing item`() =
        runTest {
            answer(405)

            assertEquals(FailureKind.Unsupported, (api.renameConversation("a", "x") as ApiResult.Failure).kind)
        }

    @Test
    fun `an upload refused with 403 pauses like a refused token`() =
        runTest {
            answer(403)

            assertEquals(UploadResult.Unauthorized, api.upload(byteArrayOf(1)))
        }

    @Test
    fun `a created token never prints its secret`() {
        val created = CreatedToken("t", "laptop", "admin", "nyt_secret")

        assertFalse(created.toString().contains("nyt_secret"))
        assertFalse("$created".contains("nyt_secret"))
    }
}
