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

    @Test
    fun `accepting answers the id of the person it ended with, null when the body has none`() =
        runTest {
            answer(200, """{"id":"p2","name":"Mykola","named":true}""")
            answer(200, """{"tags":["work"]}""")
            answer(204)
            answer(200, """{"id":"p3"}""")
            answer(409)

            assertEquals("p2", ok(api.acceptSuggestion("n1")))
            assertNull(ok(api.acceptReview("tag", "t1")))
            assertNull(ok(api.acceptReview("label", "42")))
            assertEquals("p3", ok(api.acceptReview("name", "n2")))
            assertEquals(FailureKind.Conflict, kind(api.acceptSuggestion("n3")))

            assertEquals("/api/v1/people/suggestions/n1/accept", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/review/tag/t1/accept", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/review/label/42/accept", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/review/name/n2/accept", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `a role-only suggestion and review item decode role and named`() =
        runTest {
            answer(
                200,
                """{"items":[
                {"id":"n1","name":"Repairman","role":"repairman","named":false},
                {"id":"n2","name":"Mykola"}]}""",
            )
            answer(
                200,
                """{"items":[{"kind":"name","id":"n1",
                "proposal":{"name":"Repairman","role":"repairman","named":false}}]}""",
            )

            val suggestions = ok(api.suggestions())
            assertEquals("repairman", suggestions[0].role)
            assertEquals(false, suggestions[0].named)
            assertNull(suggestions[1].role)
            assertEquals(true, suggestions[1].named)
            assertEquals(false, ok(api.review(10)).single().proposal.named)
        }

    @Test
    fun `a suggestion carries sameName, 1 where the server sends none`() =
        runTest {
            answer(200, """{"items":[{"id":"n1","name":"Аня","sameName":16},{"id":"n2","name":"Олена"}]}""")

            val items = ok(api.suggestions())

            assertEquals(16, items[0].sameName)
            assertEquals(1, items[1].sameName)
        }

    @Test
    fun `accepting every suggestion of a name posts the name and reads the person and counts`() =
        runTest {
            answer(200, """{"person":{"id":"p1","name":"Аня","named":true},"accepted":16,"skipped":2,"extra":1}""")

            val result = ok(api.acceptAllByName("Аня"))

            assertEquals(AcceptedByName("p1", 16, 2), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/people/suggestions/accept-by-name", request.url.encodedPath)
            assertEquals("""{"name":"Аня"}""", request.body?.utf8())
        }

    @Test
    fun `a body without a person or counts still decodes`() =
        runTest {
            answer(200, "{}")

            assertEquals(AcceptedByName(null, 0, 0), ok(api.acceptAllByName("Аня")))
        }

    @Test
    fun `accepting by name maps the statuses`() =
        runTest {
            answer(404)
            answer(409)
            answer(403)
            answer(400, """{"errors":{"name":["Required."]}}""")

            assertEquals(FailureKind.NotFound, kind(api.acceptAllByName("Аня")))
            assertEquals(FailureKind.Conflict, kind(api.acceptAllByName("Аня")))
            assertEquals(FailureKind.Forbidden, kind(api.acceptAllByName("Аня")))
            assertEquals(FailureKind.Invalid, kind(api.acceptAllByName(" ")))
        }
}
