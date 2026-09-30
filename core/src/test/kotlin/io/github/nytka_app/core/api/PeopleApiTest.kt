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

/** The people calls against the contract of the v0.7 server. */
class PeopleApiTest {
    private val server = MockWebServer()
    private val api =
        PeopleApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) })

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
    fun `people decodes the list`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"p1","name":"Anna","createdAt":"2026-09-29T08:00:00Z","voices":["4","7"],
                "segments":12,"extra":1}]}""",
            )

            val person = ok(api.people()).single()

            assertEquals(Person("p1", "Anna", "2026-09-29T08:00:00Z", listOf("4", "7"), 12), person)
            assertEquals("/api/v1/people", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `voices decodes a label or none`() =
        runTest {
            answer(
                200,
                """{"items":[{"speakerId":"4","label":"SPEAKER_01","segments":9,"lastSeenAt":"2026-09-29T08:00:00Z"},
                {"speakerId":"5","label":null,"segments":2,"lastSeenAt":"2026-09-28T08:00:00Z"}]}""",
            )

            val voices = ok(api.voices())

            assertEquals(listOf("SPEAKER_01", null), voices.map { it.label })
            assertEquals("/api/v1/voices", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `rename patches the name and a 409 is a conflict`() =
        runTest {
            answer(200, """{"id":"p1","name":"Anna B"}""")
            answer(409)

            assertEquals(ApiResult.Ok(Unit), api.renamePerson("p1", "Anna B"))
            val failure = api.renamePerson("p1", "Bea") as ApiResult.Failure

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/people/p1", request.url.encodedPath)
            assertEquals("""{"name":"Anna B"}""", request.body?.utf8())
            assertEquals(FailureKind.Conflict, failure.kind)
        }

    @Test
    fun `naming a voice posts the name and the speaker id`() =
        runTest {
            answer(201, "{}")

            assertEquals(ApiResult.Ok(Unit), api.nameVoice("4", "Anna"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/people", request.url.encodedPath)
            assertEquals("""{"name":"Anna","speakerId":"4"}""", request.body?.utf8())
        }

    @Test
    fun `merge posts intoId and 404 is not found`() =
        runTest {
            answer(200, """{"id":"p2","name":"Bea"}""")
            answer(404)

            assertEquals(ApiResult.Ok(Unit), api.mergePerson("p1", "p2"))
            val failure = api.mergePerson("p1", "gone") as ApiResult.Failure

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/people/p1/merge", request.url.encodedPath)
            assertEquals("""{"intoId":"p2"}""", request.body?.utf8())
            assertEquals(FailureKind.NotFound, failure.kind)
        }

    @Test
    fun `delete answers 204 and forget reads forgotten`() =
        runTest {
            answer(204)
            answer(200, """{"forgotten":true}""")
            answer(200, """{"forgotten":false}""")

            assertEquals(ApiResult.Ok(Unit), api.deletePerson("p1"))
            assertEquals(true, ok(api.forgetPerson("p1")))
            assertEquals(false, ok(api.forgetPerson("p1")))

            val plain = server.takeRequest()
            val forget = server.takeRequest()
            assertEquals("DELETE", plain.method)
            assertEquals(null, plain.url.queryParameter("forget"))
            assertEquals("/api/v1/people/p1", forget.url.encodedPath)
            assertEquals("true", forget.url.queryParameter("forget"))
        }

    @Test
    fun `a server before the contract answers 404 or 405`() =
        runTest {
            answer(404)
            answer(405)

            assertEquals(FailureKind.NotFound, (api.voices() as ApiResult.Failure).kind)
            assertEquals(FailureKind.Unsupported, (api.people() as ApiResult.Failure).kind)
        }
}
