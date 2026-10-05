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

/** The review inbox and name-suggestion calls against the contract of the server's main branch. */
class ReviewApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = ReviewApi(nytka)

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
    fun `review decodes the three kinds and asks for the limit`() =
        runTest {
            answer(
                200,
                """{"items":[
                {"kind":"name","id":"s1","conversationId":"c1","conversationTitle":"Walk","at":"2026-10-04T09:00:00Z",
                "text":"I am Olena","proposal":{"name":"Olena","personId":null,"confidence":0.9,"similarity":null,"isUser":null}},
                {"kind":"voice","id":"v1","conversationId":"c1","conversationTitle":null,"at":"2026-10-04T08:00:00Z",
                "text":"Hello","proposal":{"name":"Olena","personId":"p1","confidence":null,"similarity":0.71,"isUser":null}},
                {"kind":"label","id":"42","conversationId":"c2","conversationTitle":"Call","at":"2026-10-03T08:00:00Z",
                "text":"Yes","proposal":{"name":null,"personId":null,"confidence":null,"similarity":0.52,"isUser":true},
                "extra":1}]}""",
            )

            val items = ok(api.review(200))

            assertEquals(
                listOf(ReviewItem.KIND_NAME, ReviewItem.KIND_VOICE, ReviewItem.KIND_LABEL),
                items.map { it.kind },
            )
            assertEquals("Olena", items[0].proposal.name)
            assertEquals(0.9, items[0].proposal.confidence!!, 1e-6)
            assertEquals("p1", items[1].proposal.personId)
            assertEquals("42", items[2].id)
            assertEquals(true, items[2].proposal.isUser)
            assertNull(items[1].conversationTitle)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/review", request.url.encodedPath)
            assertEquals("200", request.url.queryParameter("limit"))
        }

    @Test
    fun `an item with its optional fields missing still decodes`() =
        runTest {
            answer(200, """{"items":[{"kind":"label","id":"42"}]}""")

            assertEquals(ReviewItem(kind = "label", id = "42"), ok(api.review(50)).single())
        }

    @Test
    fun `review on an older server is not found or unsupported`() =
        runTest {
            answer(404)
            answer(405)

            assertEquals(FailureKind.NotFound, kind(api.review(50)))
            assertEquals(FailureKind.Unsupported, kind(api.review(50)))
        }

    @Test
    fun `accept and reject post to the item's own route`() =
        runTest {
            answer(200, """{"id":"p1","name":"Olena"}""")
            answer(204)
            answer(204)

            assertEquals(ApiResult.Ok(Unit), api.answer("name", "s1", accept = true))
            assertEquals(ApiResult.Ok(Unit), api.answer("voice", "v1", accept = false))
            assertEquals(ApiResult.Ok(Unit), api.answer("label", "42", accept = true))

            val accept = server.takeRequest()
            assertEquals("POST", accept.method)
            assertEquals("/api/v1/review/name/s1/accept", accept.url.encodedPath)
            assertEquals("/api/v1/review/voice/v1/reject", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/review/label/42/accept", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `an answered item is a conflict, an unknown one not found, a read token forbidden`() =
        runTest {
            answer(409)
            answer(404)
            answer(403)

            assertEquals(FailureKind.Conflict, kind(api.answer("name", "s1", accept = true)))
            assertEquals(FailureKind.NotFound, kind(api.answer("name", "s1", accept = false)))
            assertEquals(FailureKind.Forbidden, kind(api.answer("name", "s1", accept = true)))
        }

    @Test
    fun `suggestions reads the pending ones`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"n1","conversationId":"c1","target":"speaker","speakerId":"4","groupId":null,
                "name":"Olena","personId":"p1","confidence":0.8,
                "evidence":{"segmentId":42,"startedAt":"2026-10-04T09:00:00Z","text":"I am Olena"},"extra":1}]}""",
            )

            val suggestion = ok(api.suggestions()).single()

            assertEquals("n1", suggestion.id)
            assertEquals("c1", suggestion.conversationId)
            assertEquals("speaker", suggestion.target)
            assertEquals("4", suggestion.speakerId)
            assertNull(suggestion.groupId)
            assertEquals("Olena", suggestion.name)
            assertEquals("p1", suggestion.personId)
            assertEquals(SuggestionEvidence(42, "2026-10-04T09:00:00Z", "I am Olena"), suggestion.evidence)
            val request = server.takeRequest()
            assertEquals("/api/v1/people/suggestions", request.url.encodedPath)
            assertEquals("pending", request.url.queryParameter("status"))
        }

    @Test
    fun `a suggestion without evidence still decodes and an old server is not found`() =
        runTest {
            answer(200, """{"items":[{"id":"n1"}]}""")
            answer(404)

            assertNull(ok(api.suggestions()).single().evidence)
            assertEquals(FailureKind.NotFound, kind(api.suggestions()))
        }

    @Test
    fun `suggestions are answered on their own routes`() =
        runTest {
            answer(200, """{"id":"p1","name":"Olena"}""")
            answer(204)
            answer(409)

            assertEquals(ApiResult.Ok(Unit), api.answerSuggestion("n1", accept = true))
            assertEquals(ApiResult.Ok(Unit), api.answerSuggestion("n1", accept = false))
            assertEquals(FailureKind.Conflict, kind(api.answerSuggestion("n2", accept = true)))

            val accept = server.takeRequest()
            assertEquals("POST", accept.method)
            assertEquals("/api/v1/people/suggestions/n1/accept", accept.url.encodedPath)
            assertEquals("/api/v1/people/suggestions/n1/reject", server.takeRequest().url.encodedPath)
        }
}
