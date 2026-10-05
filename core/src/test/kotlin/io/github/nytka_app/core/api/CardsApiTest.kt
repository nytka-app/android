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

/** The voice-card calls against the contract of server 0.14. */
class CardsApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = CardsApi(nytka)

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

    private val group = VoiceCard(kind = "group", id = "g1")
    private val match = VoiceCard(kind = "match", id = "m1", personId = "p1", personName = "Olena")

    private fun body() = server.takeRequest().body?.utf8()

    @Test
    fun `cards decodes a group and a match`() =
        runTest {
            answer(
                200,
                """{"items":[
                {"kind":"group","id":"g1","conversationId":"c1","conversationTitle":"Walk","personId":null,
                "personName":null,"similarity":null,"clip":{"from":"2026-10-04T09:00:00Z","until":"2026-10-04T09:00:08Z"},
                "lines":[{"segmentId":42,"startedAt":"2026-10-04T09:00:00Z","text":"Hello"}],"extra":1},
                {"kind":"match","id":"m1","conversationId":"c2","conversationTitle":null,"personId":"p1",
                "personName":"Olena","similarity":0.73,"clip":{"from":"a","until":"b"},"lines":[]}]}""",
            )

            val cards = ok(api.cards())

            assertEquals(listOf(VoiceCard.KIND_GROUP, VoiceCard.KIND_MATCH), cards.map { it.kind })
            assertNull(cards[0].personId)
            assertEquals(CardClip("2026-10-04T09:00:00Z", "2026-10-04T09:00:08Z"), cards[0].clip)
            assertEquals(listOf(CardLine(42, "2026-10-04T09:00:00Z", "Hello")), cards[0].lines)
            assertEquals("Olena", cards[1].personName)
            assertEquals(0.73, cards[1].similarity!!, 1e-6)
            assertEquals("/api/v1/people/cards", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `a card with its optional fields missing still decodes`() =
        runTest {
            answer(200, """{"items":[{"kind":"group","id":"g1"}]}""")

            assertEquals(group, ok(api.cards()).single())
        }

    @Test
    fun `cards on an older server are not found or unsupported`() =
        runTest {
            answer(404)
            answer(405)

            assertEquals(FailureKind.NotFound, kind(api.cards()))
            assertEquals(FailureKind.Unsupported, kind(api.cards()))
        }

    @Test
    fun `each answer sends exactly one field to the card's route`() =
        runTest {
            repeat(3) { answer(200, """{"id":"p1","name":"Olena"}""") }
            answer(204)
            answer(204)

            assertEquals(ApiResult.Ok(Unit), api.answer(group, CardAnswer.Person("p1")))
            assertEquals(ApiResult.Ok(Unit), api.answer(group, CardAnswer.Name("Olena")))
            assertEquals(ApiResult.Ok(Unit), api.answer(match, CardAnswer.Person("p1")))
            assertEquals(ApiResult.Ok(Unit), api.answer(group, CardAnswer.Skip))
            assertEquals(ApiResult.Ok(Unit), api.answer(match, CardAnswer.Reject))

            val first = server.takeRequest()
            assertEquals("POST", first.method)
            assertEquals("/api/v1/people/cards/group/g1", first.url.encodedPath)
            assertEquals("""{"personId":"p1"}""", first.body?.utf8())
            assertEquals("""{"name":"Olena"}""", body())
            val third = server.takeRequest()
            assertEquals("/api/v1/people/cards/match/m1", third.url.encodedPath)
            assertEquals("""{"personId":"p1"}""", third.body?.utf8())
            assertEquals("""{"skip":true}""", body())
            assertEquals("""{"reject":true}""", body())
        }

    @Test
    fun `a gone card is not found, a wrong person is invalid, a read token forbidden`() =
        runTest {
            answer(404)
            answer(400, """{"errors":{"personId":["Must be the person of this match."]}}""")
            answer(403)

            assertEquals(FailureKind.NotFound, kind(api.answer(group, CardAnswer.Skip)))
            assertEquals(FailureKind.Invalid, kind(api.answer(match, CardAnswer.Person("p2"))))
            assertEquals(FailureKind.Forbidden, kind(api.answer(group, CardAnswer.Skip)))
        }

    @Test
    fun `the clip request carries the url and the header, and hides the token`() =
        runTest {
            val request = api.clipRequest("group", "g1")!!

            assertEquals(server.url("/api/v1/people/cards/group/g1/clip").toString(), request.url)
            assertEquals("Bearer token-1", request.authorization)
            assertFalse(request.toString().contains("token-1"))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `no clip request without a token`() =
        runTest {
            val unset = CardsApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "") })

            assertNull(unset.clipRequest("group", "g1"))
        }
}
