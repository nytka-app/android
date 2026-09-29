package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class V04ClientsTest {
    private val server = MockWebServer()
    private lateinit var nytka: NytkaApi

    @Before
    fun start() {
        server.start()
        val settings = Settings(serverUrl = server.url("/").toString(), token = "t", privateNetwork = true)
        nytka = NytkaApi(OkHttpClient()) { settings }
    }

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

    private val memory =
        """{"id":"m1","text":"Lives in Kyiv","source":"ai","conversationId":"c1","conversationTitle":"Lunch",""" +
            """"conversationStartedAt":"2026-09-28T10:00:00Z","createdAt":"2026-09-28T11:00:00Z",""" +
            """"updatedAt":"2026-09-28T11:00:00Z","extra":1}"""

    @Test
    fun `memories reads a page and sends the cursor`() =
        runTest {
            answer(200, """{"items":[$memory],"nextBefore":"m1"}""")

            val page = (MemoriesApi(nytka).memories("m9", 50) as ApiResult.Ok).value

            assertEquals("Lives in Kyiv", page.items.single().text)
            assertEquals("m1", page.nextBefore)
            val request = server.takeRequest()
            assertEquals(
                "/api/v1/memories?before=m9&limit=50",
                request.url.encodedPath + "?" + request.url.encodedQuery,
            )
        }

    @Test
    fun `memory writes send the text as JSON and map refusals`() =
        runTest {
            val api = MemoriesApi(nytka)
            answer(201, memory)
            answer(409)
            answer(200, memory)
            answer(204)

            assertTrue(api.addMemory("Lives in Kyiv") is ApiResult.Ok)
            assertEquals(FailureKind.Conflict, (api.addMemory("Lives in Kyiv") as ApiResult.Failure).kind)
            assertTrue(api.editMemory("m1", "x") is ApiResult.Ok)
            assertTrue(api.deleteMemory("m1") is ApiResult.Ok)

            val add = server.takeRequest()
            assertEquals("POST", add.method)
            assertEquals("""{"text":"Lives in Kyiv"}""", add.body?.utf8())
            server.takeRequest()
            val edit = server.takeRequest()
            assertEquals("PATCH /api/v1/memories/m1", "${edit.method} ${edit.url.encodedPath}")
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun `an older server answers 404`() =
        runTest {
            answer(404)

            val result = SearchApi(nytka).search("kyiv", emptySet(), 20, 0)

            assertEquals(FailureKind.NotFound, (result as ApiResult.Failure).kind)
        }

    @Test
    fun `search sends the query, the kinds and the offset`() =
        runTest {
            answer(
                200,
                """{"items":[{"kind":"memory","id":"m1","score":0.4,"title":null,"snippet":"<mark>Kyiv</mark>",""" +
                    """"at":"2026-09-28T11:00:00Z","conversationId":null}],"nextOffset":null}""",
            )

            val page = (SearchApi(nytka).search("kyiv", setOf("memory", "conversation"), 20, 40) as ApiResult.Ok).value

            assertEquals("memory", page.items.single().kind)
            assertEquals(null, page.nextOffset)
            val request = server.takeRequest()
            assertEquals("kyiv", request.url.queryParameter("q"))
            assertEquals("conversation,memory", request.url.queryParameter("kinds"))
            assertEquals("40", request.url.queryParameter("offset"))
        }

    @Test
    fun `search leaves kinds out when empty`() =
        runTest {
            answer(200, """{"items":[]}""")

            SearchApi(nytka).search("kyiv", emptySet(), 20, 0)

            assertEquals(null, server.takeRequest().url.queryParameter("kinds"))
        }

    private val hook =
        """{"id":"w1","url":"https://x.test/h","events":["*"],"description":null,"active":true,""" +
            """"createdAt":"2026-09-28T11:00:00Z","lastDelivery":{"status":"delivered","at":"2026-09-28T12:00:00Z"}}"""

    @Test
    fun `webhooks list, create with the secret, patch, test and deliveries`() =
        runTest {
            val api = WebhooksApi(nytka)
            answer(200, """{"items":[$hook]}""")
            answer(201, hook.dropLast(1) + ""","secret":"whsec_abc"}""")
            answer(200, hook)
            answer(202, """{"deliveryId":"d1"}""")
            answer(
                200,
                """{"items":[{"id":"d1","eventType":"ping","status":"failed","attempts":6,"lastStatusCode":500,""" +
                    """"lastError":"HTTP 500","createdAt":"2026-09-28T12:00:00Z","deliveredAt":null}]}""",
            )

            assertEquals(
                "delivered",
                (api.webhooks() as ApiResult.Ok)
                    .value
                    .single()
                    .lastDelivery
                    ?.status,
            )
            val created = (api.createWebhook("https://x.test/h", listOf("*"), null) as ApiResult.Ok).value
            assertEquals("whsec_abc", created.secret)
            assertTrue(api.setWebhookActive("w1", false) is ApiResult.Ok)
            assertEquals("d1", (api.testWebhook("w1") as ApiResult.Ok).value)
            assertEquals(6, (api.deliveries("w1", 30) as ApiResult.Ok).value.single().attempts)

            server.takeRequest()
            val create = server.takeRequest()
            assertEquals("""{"url":"https://x.test/h","events":["*"]}""", create.body?.utf8())
            assertEquals("""{"active":false}""", server.takeRequest().body?.utf8())
            assertEquals("/api/v1/webhooks/w1/test", server.takeRequest().url.encodedPath)
            assertEquals("30", server.takeRequest().url.queryParameter("limit"))
        }

    @Test
    fun `a read token is forbidden from webhooks`() =
        runTest {
            answer(403)

            assertEquals(FailureKind.Forbidden, (WebhooksApi(nytka).webhooks() as ApiResult.Failure).kind)
        }
}
