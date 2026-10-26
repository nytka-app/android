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

/** Today, the Timeline, insights and item marks, against the contract agreed for step 5 of the redesign. */
class TodayApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val today = TodayApi(nytka)
    private val insights = InsightsApi(nytka)
    private val marks = ItemMarksApi(nytka)

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
    fun `today sends the zone and decodes items with their cited lines`() =
        runTest {
            answer(
                200,
                """{"date":"2026-10-23","items":[
                {"kind":"task","id":"t1","text":"Send the draft","salience":"vital","at":"2026-10-23T06:14:02Z",
                "conversationId":"c1","personId":"p1","personName":"Ann","due":"2026-10-24","done":false,"part":"morning",
                "evidence":[{"segmentId":48121,"conversationId":"c1","at":"2026-10-23T06:14:02Z","speaker":"Wearer",
                "isUser":true,"personId":null,"quote":"I'll send it tomorrow.","wearerConfidence":0.66,"extra":1}]},
                {"kind":"memory","id":"m1","text":"Moved to a new flat","salienceManual":true,
                "markedAt":"2026-10-23T07:01:00Z"}]}""",
            )

            val page = ok(today.today("Europe/Kyiv"))

            val request = server.takeRequest()
            assertEquals("/api/v1/today", request.url.encodedPath)
            assertEquals("Europe/Kyiv", request.url.queryParameter("tz"))
            assertEquals("2026-10-23", page.date)
            val task = page.items[0]
            assertEquals(DayPart.MORNING, task.part)
            assertEquals(
                Evidence(48121, "c1", "2026-10-23T06:14:02Z", "I'll send it tomorrow.", "Wearer", true, null, 0.66),
                task.evidence.single(),
            )
            val memory = page.items[1]
            assertTrue(memory.salienceManual)
            assertEquals("2026-10-23T07:01:00Z", memory.markedAt)
            assertNull(task.markedAt)
            assertNull(memory.salience)
            assertEquals(emptyList<Evidence>(), memory.evidence)
        }

    @Test
    fun `timeline asks for the day and keeps noise scenes`() =
        runTest {
            answer(
                200,
                """{"date":"2026-10-22","scenes":[{"id":"s1","conversationId":"c1","startedAt":"2026-10-22T07:00:00Z",
                "endedAt":"2026-10-22T07:20:00Z","title":"Standup","people":[{"name":"Voice 2"},{"name":"Ann","personId":"p1"}],
                "items":[]},{"id":"s2","conversationId":"c2","startedAt":"2026-10-22T08:00:00Z",
                "endedAt":"2026-10-22T08:00:40Z","noise":true},{"id":"s3","conversationId":"c3",
                "startedAt":"2026-10-22T09:00:00Z","endedAt":null}]}""",
            )

            val timeline = ok(today.timeline("2026-10-22", "Europe/Kyiv"))

            val request = server.takeRequest()
            assertEquals("/api/v1/timeline", request.url.encodedPath)
            assertEquals("2026-10-22", request.url.queryParameter("date"))
            assertEquals(listOf(ScenePerson("Voice 2"), ScenePerson("Ann", "p1")), timeline.scenes[0].people)
            assertFalse(timeline.scenes[0].noise)
            assertTrue(timeline.scenes[1].noise)
            assertNull(timeline.scenes[2].endedAt)
        }

    @Test
    fun `a server without the routes needs an update`() =
        runTest {
            answer(404)
            assertEquals(FailureKind.NotFound, kind(today.today("UTC")))
        }

    @Test
    fun `insights decode locked progress and unlocked series`() =
        runTest {
            answer(
                200,
                """{"daysWithAudio":9,"trustedAfterDays":14,"insights":[
                {"id":"talk","title":"Talk time","unlocked":false,"progress":{"have":9,"need":14,"unit":"days"}},
                {"id":"people","title":"Who you see","unlocked":true,"summary":"Mostly Ann.",
                "series":[{"date":"2026-10-21","value":3},{"date":"2026-10-22","value":5}],"baseline":4.0}]}""",
            )

            val answer = ok(insights.insights())

            assertEquals("/api/v1/insights", server.takeRequest().url.encodedPath)
            assertEquals(InsightProgress(9, 14, "days"), answer.insights[0].progress)
            assertEquals(listOf(3.0, 5.0), answer.insights[1].series.map { it.value })
            assertEquals(4.0, answer.insights[1].baseline)
        }

    @Test
    fun `not important patches the item's salience`() =
        runTest {
            answer(200, "{}")
            ok(marks.setSalience(LifeItem.KIND_MEMORY, "m1", Salience.NOISE))

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/memories/m1", request.url.encodedPath)
            assertEquals("""{"salience":"noise"}""", request.body!!.utf8())
        }

    @Test
    fun `undo clears the salience mark`() =
        runTest {
            answer(200, "{}")
            ok(marks.setSalience(LifeItem.KIND_TASK, "t1", null))

            val request = server.takeRequest()
            assertEquals("/api/v1/tasks/t1", request.url.encodedPath)
            assertEquals("""{"salience":null}""", request.body!!.utf8())
        }

    @Test
    fun `not me and wrong person patch the cited line`() =
        runTest {
            answer(200, "{}")
            answer(200, "{}")
            ok(marks.markLine(48121, false))
            ok(marks.setLinePerson(48121, "p2"))

            val first = server.takeRequest()
            assertEquals("/api/v1/segments/48121", first.url.encodedPath)
            assertEquals("""{"isUser":false}""", first.body!!.utf8())
            assertEquals("""{"personId":"p2"}""", server.takeRequest().body!!.utf8())
        }
}
