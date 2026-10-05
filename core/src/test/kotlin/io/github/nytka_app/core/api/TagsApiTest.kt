package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

/**
 * The tag calls against nytka-app/server `docs/specs/tags.md`. The routes of proposals (`suggestions`,
 * `answerSuggestion`) are those of server T-4, not released when this was written.
 */
class TagsApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = TagsApi(nytka)

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
    fun `tags reads the list with the prefix and maps the counts`() =
        runTest {
            answer(
                200,
                """{"items":[{"name":"work","conversations":3,"people":1,"uses":4},
                {"name":"робота","conversations":1,"people":0,"uses":1}]}""",
            )

            val tags = ok(api.tags("wo"))

            assertEquals(listOf(Tag("work", 3, 1, 4), Tag("робота", 1, 0, 1)), tags)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/tags?q=wo", request.url.encodedPath + "?" + request.url.encodedQuery)
            assertEquals("Bearer token-1", request.headers["Authorization"])
        }

    @Test
    fun `tags without a prefix sends no query`() =
        runTest {
            answer(200, """{"items":[]}""")

            assertEquals(emptyList<Tag>(), ok(api.tags(null)))

            assertNull(server.takeRequest().url.encodedQuery)
        }

    @Test
    fun `adding a conversation tag puts it and returns the sorted tags`() =
        runTest {
            answer(200, """{"tags":["family","work"]}""")

            val tags = ok(api.addConversationTag("c1", "work"))

            assertEquals(listOf("family", "work"), tags)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/conversations/c1/tags/work", request.url.encodedPath)
        }

    @Test
    fun `removing a conversation tag deletes it and returns what is left`() =
        runTest {
            answer(200, """{"tags":["family"]}""")

            assertEquals(listOf("family"), ok(api.removeConversationTag("c1", "work")))

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/conversations/c1/tags/work", request.url.encodedPath)
        }

    @Test
    fun `person tags use the people path`() =
        runTest {
            answer(200, """{"tags":["repairman"]}""")
            answer(200, """{"tags":[]}""")

            assertEquals(listOf("repairman"), ok(api.addPersonTag("p1", "repairman")))
            assertEquals(emptyList<String>(), ok(api.removePersonTag("p1", "repairman")))

            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/api/v1/people/p1/tags/repairman", put.url.encodedPath)
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertEquals("/api/v1/people/p1/tags/repairman", delete.url.encodedPath)
        }

    @Test
    fun `a Cyrillic tag name is percent-encoded in the path`() =
        runTest {
            answer(200, """{"tags":["робота"]}""")
            answer(200, """{"tags":[]}""")

            assertEquals(listOf("робота"), ok(api.addConversationTag("c1", "робота")))
            ok(api.removePersonTag("p1", "робота"))

            // робота
            val encoded = "%D1%80%D0%BE%D0%B1%D0%BE%D1%82%D0%B0"
            assertEquals("/api/v1/conversations/c1/tags/$encoded", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/people/p1/tags/$encoded", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `a name with a slash, a space or a hash stays one path segment`() =
        runTest {
            answer(
                400,
                """{"errors":{"name":["Must be 1 to 32 letters, digits, - or _, starting with a letter or digit."]}}""",
            )

            val failure = api.addConversationTag("c1", "a/b #c") as ApiResult.Failure

            assertEquals(FailureKind.Invalid, failure.kind)
            assertEquals("/api/v1/conversations/c1/tags/a%2Fb%20%23c", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `conversations with a tag sends tag, before and limit`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"c1","startedAt":"2026-10-04T09:00:00Z","endedAt":"2026-10-04T09:10:00Z",
                "status":"done","preview":"Hi","tags":["робота"]}],"nextBefore":"2026-10-04T09:00:00Z"}""",
            )

            val page = ok(api.conversations("робота", "2026-10-05T00:00:00Z", 30))

            assertEquals(listOf("робота"), page.items.single().tags)
            assertEquals("2026-10-04T09:00:00Z", page.nextBefore)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/conversations", request.url.encodedPath)
            assertEquals("робота", request.url.queryParameter("tag"))
            assertEquals("2026-10-05T00:00:00Z", request.url.queryParameter("before"))
            assertEquals("30", request.url.queryParameter("limit"))
        }

    @Test
    fun `people with a tag sends tag and decodes tags`() =
        runTest {
            answer(
                200,
                """{"items":[{"id":"p1","name":"Olena","createdAt":"2026-09-01T08:00:00Z","voices":[],"segments":2,
                "tags":["family"]}]}""",
            )

            val people = ok(api.people("family"))

            assertEquals(listOf("family"), people.single().tags)
            assertTrue(people.single().named)
            val request = server.takeRequest()
            assertEquals("/api/v1/people", request.url.encodedPath)
            assertEquals("family", request.url.queryParameter("tag"))
        }

    @Test
    fun `suggestions asks for the pending ones and maps both kinds`() =
        runTest {
            answer(
                200,
                """{"items":[
                {"id":"s1","conversationId":"c1","personId":null,"personName":null,"name":"work",
                "createdAt":"2026-10-05T08:00:00Z"},
                {"id":"s2","conversationId":"c1","personId":"p1","personName":"Olena","name":"family",
                "createdAt":"2026-10-05T08:01:00Z"}]}""",
            )

            val suggestions = ok(api.suggestions())

            assertEquals(
                listOf(
                    TagSuggestion("s1", "c1", null, null, "work", "2026-10-05T08:00:00Z"),
                    TagSuggestion("s2", "c1", "p1", "Olena", "family", "2026-10-05T08:01:00Z"),
                ),
                suggestions,
            )
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/tags/suggestions", request.url.encodedPath)
            assertEquals("pending", request.url.queryParameter("status"))
        }

    @Test
    fun `answering a suggestion posts accept or reject and takes 200 or 204`() =
        runTest {
            answer(200, """{"tags":["work"]}""")
            answer(204)

            ok(api.answerSuggestion("s1", accept = true))
            ok(api.answerSuggestion("s2", accept = false))

            val accept = server.takeRequest()
            assertEquals("POST", accept.method)
            assertEquals("/api/v1/tags/suggestions/s1/accept", accept.url.encodedPath)
            val reject = server.takeRequest()
            assertEquals("POST", reject.method)
            assertEquals("/api/v1/tags/suggestions/s2/reject", reject.url.encodedPath)
        }

    @Test
    fun `a 204 is Ok of Unit`() =
        runTest {
            answer(204)

            assertEquals(ApiResult.Ok(Unit), api.answerSuggestion("s1", accept = false))
        }

    @Test
    fun `400, 403, 404 and 409 map to their kinds`() =
        runTest {
            answer(400, """{"errors":{"name":["Must be 1 to 32 letters."]}}""")
            answer(403)
            answer(404)
            answer(409, """{"title":"This item has 20 tags."}""")

            assertEquals(FailureKind.Invalid, kind(api.addConversationTag("c1", "x y/z")))
            assertEquals(FailureKind.Forbidden, kind(api.addPersonTag("p1", "work")))
            assertEquals(FailureKind.NotFound, kind(api.removeConversationTag("c1", "work")))
            assertEquals(FailureKind.Conflict, kind(api.addConversationTag("c1", "work")))
        }

    @Test
    fun `a failure never carries the tag name`() =
        runTest {
            answer(409)

            val failure = api.addConversationTag("c1", "therapy") as ApiResult.Failure

            assertFalse(failure.message.contains("therapy"))
        }

    @Test
    fun `models decode without the new fields and take the defaults`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        val summary =
            json.decodeFromString<ConversationSummary>(
                """{"id":"c1","startedAt":"a","endedAt":"b","status":"done","preview":"p"}""",
            )
        val detail =
            json.decodeFromString<ConversationDetail>(
                """{"id":"c1","startedAt":"a","endedAt":"b","status":"done","segments":[]}""",
            )
        val person = json.decodeFromString<Person>("""{"id":"p1","name":"Olena"}""")
        val page = json.decodeFromString<PersonPage>("""{"id":"p1"}""")
        val suggestion = json.decodeFromString<NameSuggestion>("""{"id":"s1","name":"Olena"}""")
        val proposal = json.decodeFromString<ReviewProposal>("""{"name":"Olena"}""")

        assertEquals(emptyList<String>(), summary.tags)
        assertEquals(emptyList<String>(), detail.tags)
        assertEquals(emptyList<String>(), person.tags)
        assertTrue(person.named)
        assertEquals(emptyList<String>(), page.tags)
        assertTrue(page.named)
        assertNull(suggestion.role)
        assertTrue(suggestion.named)
        assertNull(proposal.tag)
        assertNull(proposal.role)
        assertTrue(proposal.named)
    }

    @Test
    fun `models decode the new fields`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        val person =
            json.decodeFromString<Person>(
                """{"id":"p1","name":"Repairman","tags":["repairman"],"named":false}""",
            )
        val page = json.decodeFromString<PersonPage>("""{"id":"p1","tags":["a"],"named":false}""")
        val suggestion =
            json.decodeFromString<NameSuggestion>("""{"id":"s1","name":"Repairman","role":"repairman","named":false}""")
        val proposal = json.decodeFromString<ReviewProposal>("""{"tag":"work","role":null,"named":null}""")

        assertEquals(listOf("repairman"), person.tags)
        assertFalse(person.named)
        assertEquals(listOf("a"), page.tags)
        assertFalse(page.named)
        assertEquals("repairman", suggestion.role)
        assertFalse(suggestion.named)
        assertEquals("work", proposal.tag)
        assertTrue(proposal.named)
    }

    @Test
    fun `the feature names are those the server lists`() {
        val info =
            Json.decodeFromString<ServerInfo>(
                """{"serverVersion":"0.17.0","apiVersion":1,"features":["tags","tag-suggestions","roles"]}""",
            )

        assertTrue(info.has(ServerInfo.FEATURE_TAGS))
        assertTrue(info.has(ServerInfo.FEATURE_TAG_SUGGESTIONS))
        assertTrue(info.has(ServerInfo.FEATURE_ROLES))
    }
}
