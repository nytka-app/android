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

/** The person page calls against the contract of server 0.14. */
class PersonPageApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = PersonPageApi(nytka)

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

    private fun kind(result: ApiResult<*>) = (result as ApiResult.Failure).kind

    @Test
    fun `the person page maps every field and ignores an extra one`() =
        runTest {
            answer(
                200,
                """{"id":"p1","name":"Olena","note":"Met at the gym","createdAt":"2026-09-01T08:00:00Z",
                "lastSeenAt":"2026-10-04T10:00:00Z","voices":["4"],"hasVoiceprint":true,"voiceprintSamples":7,
                "conversations":[{"id":"c1","title":null,"startedAt":"2026-10-04T09:00:00Z"}],
                "facts":[{"id":"f1","personId":"p1","text":"Lives in Lviv","source":"ai","basis":"said",
                "conversationId":"c1","conversationTitle":"Walk","segmentId":42,"createdAt":"2026-10-04T10:00:00Z",
                "updatedAt":"2026-10-04T10:00:00Z"}],
                "openTasks":[{"id":"t1","conversationId":"c1","conversationTitle":"Walk",
                "conversationStartedAt":"2026-10-04T09:00:00Z","text":"Send photos","done":false,"doneAt":null,
                "createdAt":"2026-10-04T10:00:00Z","personId":"p1","personName":"Olena"}],"extra":1}""",
            )

            val page = ok(api.person("p1"))

            assertEquals("Olena", page.name)
            assertEquals("Met at the gym", page.note)
            assertEquals("2026-10-04T10:00:00Z", page.lastSeenAt)
            assertEquals(listOf("4"), page.voices)
            assertTrue(page.hasVoiceprint)
            assertEquals(7, page.voiceprintSamples)
            assertEquals(PersonConversation("c1", null, "2026-10-04T09:00:00Z"), page.conversations.single())
            val fact = page.facts.single()
            assertEquals(
                listOf("f1", "p1", "Lives in Lviv", "ai", "said", "c1", "Walk"),
                fact.run {
                    listOf(id, personId, text, source, basis, conversationId, conversationTitle)
                },
            )
            assertEquals(42L, fact.segmentId)
            assertEquals("Send photos", page.openTasks.single().text)
            assertEquals("Olena", page.openTasks.single().personName)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/people/p1", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
        }

    @Test
    fun `a page with its optional fields missing still decodes`() =
        runTest {
            answer(200, """{"id":"p1","name":"Olena"}""")

            val page = ok(api.person("p1"))

            assertEquals(PersonPage(id = "p1", name = "Olena"), page)
            assertNull(page.note)
            assertNull(page.lastSeenAt)
            assertFalse(page.hasVoiceprint)
        }

    @Test
    fun `an unknown person is not found and a server without the route is unsupported`() =
        runTest {
            answer(404)
            answer(405)

            assertEquals(FailureKind.NotFound, kind(api.person("p1")))
            assertEquals(FailureKind.Unsupported, kind(api.person("p1")))
        }

    @Test
    fun `keep sends no note, clear sends null and set sends the text`() =
        runTest {
            repeat(3) { answer(200, """{"id":"p1","name":"Olena","note":null}""") }

            ok(api.update("p1", "Olena B", NoteChange.Keep))
            ok(api.update("p1", null, NoteChange.Clear))
            ok(api.update("p1", null, NoteChange.Set("Met at the gym")))

            val keep = server.takeRequest()
            assertEquals("PATCH", keep.method)
            assertEquals("/api/v1/people/p1", keep.url.encodedPath)
            assertEquals("""{"name":"Olena B"}""", keep.body?.utf8())
            assertEquals("""{"note":null}""", server.takeRequest().body?.utf8())
            assertEquals("""{"note":"Met at the gym"}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun `update answers the person as stored and a taken name is a conflict`() =
        runTest {
            answer(200, """{"id":"p1","name":"Olena","note":"Hi","voices":[],"segments":3}""")
            answer(409)

            assertEquals("Hi", ok(api.update("p1", null, NoteChange.Set("Hi"))).note)
            assertEquals(FailureKind.Conflict, kind(api.update("p1", "Anna", NoteChange.Keep)))
        }

    @Test
    fun `a 400 on the note carries the field message`() =
        runTest {
            answer(400, """{"errors":{"note":["Must be text of 1 to 500 characters, or null."]}}""")

            val failure = api.update("p1", null, NoteChange.Set("x")) as ApiResult.Failure

            assertEquals(FailureKind.Invalid, failure.kind)
            assertEquals(listOf("Must be text of 1 to 500 characters, or null."), failure.errors["note"])
        }

    @Test
    fun `facts reads a page with before and limit`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"f1","personId":"p1","text":"Lives in Lviv","source":"user","basis":null,
                "conversationId":null,"conversationTitle":null,"segmentId":null,"createdAt":"2026-10-04T10:00:00Z",
                "updatedAt":"2026-10-04T10:00:00Z"}],"nextBefore":"f0"}""",
            )

            val page = ok(api.facts("p1", "f9", 50))

            assertNull(page.items.single().basis)
            assertEquals("f0", page.nextBefore)
            val request = server.takeRequest()
            assertEquals("/api/v1/people/p1/facts", request.url.encodedPath)
            assertEquals("f9", request.url.queryParameter("before"))
            assertEquals("50", request.url.queryParameter("limit"))
        }

    @Test
    fun `the first page of facts sends no before`() =
        runTest {
            answer(200, """{"items":[],"nextBefore":null}""")

            assertEquals(FactPage(), ok(api.facts("p1", null, 50)))
            assertNull(server.takeRequest().url.queryParameter("before"))
        }

    @Test
    fun `adding a fact posts the text, and a held fact is a conflict`() =
        runTest {
            answer(201, """{"id":"f2","personId":"p1","text":"Has a dog","source":"user"}""")
            answer(409)

            val fact = ok(api.addFact("p1", "Has a dog"))
            assertEquals(FailureKind.Conflict, kind(api.addFact("p1", "Has a dog")))

            assertEquals("f2", fact.id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/people/p1/facts", request.url.encodedPath)
            assertEquals("""{"text":"Has a dog"}""", request.body?.utf8())
        }

    @Test
    fun `editing a fact patches its text`() =
        runTest {
            answer(200, """{"id":"f2","personId":"p1","text":"Has two dogs","source":"user"}""")

            assertEquals("Has two dogs", ok(api.editFact("p1", "f2", "Has two dogs")).text)

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/people/p1/facts/f2", request.url.encodedPath)
            assertEquals("""{"text":"Has two dogs"}""", request.body?.utf8())
        }

    @Test
    fun `deleting a fact answers 204 as ok and an unknown fact is not found`() =
        runTest {
            answer(204)
            answer(404)

            assertEquals(ApiResult.Ok(Unit), api.deleteFact("p1", "f2"))
            assertEquals(FailureKind.NotFound, kind(api.deleteFact("p1", "f2")))

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/people/p1/facts/f2", request.url.encodedPath)
        }

    @Test
    fun `a read token is forbidden to edit`() =
        runTest {
            answer(403)

            assertEquals(FailureKind.Forbidden, kind(api.addFact("p1", "x")))
        }
}
