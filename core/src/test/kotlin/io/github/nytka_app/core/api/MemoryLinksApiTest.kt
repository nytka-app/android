package io.github.nytka_app.core.api

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Earlier moments, topic pages and summaries over time, against the contract agreed with the server session. */
class MemoryLinksApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun answer(body: String) =
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(body)
                .build(),
        )

    private fun <T> ok(result: ApiResult<T>): T = (result as ApiResult.Ok).value

    @Test
    fun `related asks by the kind of what it is about and decodes the moments`() =
        runTest {
            answer(
                """{"items":[{"kind":"conversation","conversationId":"c0","title":"Lease call","at":"2026-10-20T09:00:00Z",
                "segmentId":77,"quote":"The landlord wants the draft.","score":0.81,"reasons":["meaning","person"],
                "people":["Ann"],"tags":["lease"]}]}""",
            )

            val moments = ok(RelatedApi(nytka).related(LifeItem.KIND_TASK, "t1"))

            val request = server.takeRequest()
            assertEquals("/api/v1/related", request.url.encodedPath)
            assertEquals("t1", request.url.queryParameter("taskId"))
            assertEquals("5", request.url.queryParameter("limit"))
            assertEquals(listOf("meaning", "person"), moments.single().reasons)
            assertEquals(77L, moments.single().segmentId)
        }

    @Test
    fun `topics list by kind and one topic pages its moments`() =
        runTest {
            answer("""{"items":[{"id":"tp1","kind":"tag","name":"lease","moments":12,"openCommitments":2}]}""")
            answer(
                """{"id":"tp1","kind":"tag","name":"lease","summary":"The flat lease.","timeline":[
                {"conversationId":"c1","at":"2026-10-23T08:40:24Z","title":"Planning","line":"I'll send it.","segmentId":103}],
                "commitments":[{"kind":"task","id":"t1","text":"Send the draft"}],"nextBefore":"cur-2"}""",
            )
            val api = TopicsApi(nytka)

            val list = ok(api.topics(Topic.KIND_TAG))
            val page = ok(api.topic("tp1", before = "cur-1"))

            assertEquals("tag", server.takeRequest().url.queryParameter("kind"))
            val second = server.takeRequest()
            assertEquals("/api/v1/topics/tp1", second.url.encodedPath)
            assertEquals("cur-1", second.url.queryParameter("before"))
            assertEquals(2, list.single().openCommitments)
            assertEquals("cur-2", page.nextBefore)
            assertEquals("t1", page.commitments.single().id)
            assertEquals(103L, page.timeline.single().segmentId)
        }

    @Test
    fun `rollups decode an open summary and a locked one`() =
        runTest {
            answer(
                """{"items":[{"period":"week","start":"2026-10-19","end":"2026-10-25","summary":"A lease week.",
                "changes":["More time with Ann"],"topics":[{"topicId":"tp1","name":"lease","line":"Draft sent"}],
                "sources":{"digests":5,"conversations":12}},
                {"period":"year","start":"2026-01-01","end":"2026-12-31","locked":true,
                "progress":{"have":18,"need":60,"unit":"days"}}]}""",
            )

            val rollups = ok(RollupsApi(nytka).rollups(Rollup.WEEK, limit = 2))

            val request = server.takeRequest()
            assertEquals("week", request.url.queryParameter("period"))
            assertNull(request.url.queryParameter("before"))
            assertEquals(12, rollups[0].sources.conversations)
            assertTrue(rollups[1].locked)
            assertNull(rollups[1].summary)
            assertEquals(InsightProgress(18, 60), rollups[1].progress)
        }
}
