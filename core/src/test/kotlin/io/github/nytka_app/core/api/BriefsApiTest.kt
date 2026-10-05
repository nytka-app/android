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

/** The upcoming-briefs call against the contract of the server's main branch. */
class BriefsApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = BriefsApi(nytka)

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
    fun `upcoming decodes events with and without a brief`() =
        runTest {
            answer(
                200,
                """{"items":[
                {"uid":"e1","title":"Lunch with Olena","startsAt":"2026-10-05T12:00:00Z","endsAt":"2026-10-05T13:00:00Z",
                "attendees":[{"name":"Olena","personId":"p1"},{"name":"Bob","personId":null}],
                "brief":{"id":"b1","text":"She moved to Lviv.","createdAt":"2026-10-05T11:30:00Z"},"extra":1},
                {"uid":"e2","title":"Standup","startsAt":"2026-10-05T14:00:00Z","endsAt":"2026-10-05T14:15:00Z",
                "attendees":[],"brief":null}]}""",
            )

            val events = ok(api.upcoming(240))

            assertEquals(listOf("e1", "e2"), events.map { it.uid })
            assertEquals(listOf(BriefAttendee("Olena", "p1"), BriefAttendee("Bob", null)), events[0].attendees)
            assertEquals(BriefText("b1", "She moved to Lviv.", "2026-10-05T11:30:00Z"), events[0].brief)
            assertNull(events[1].brief)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/briefs/upcoming", request.url.encodedPath)
            assertEquals("240", request.url.queryParameter("minutes"))
        }

    @Test
    fun `an event with its optional fields missing still decodes`() =
        runTest {
            answer(200, """{"items":[{"uid":"e1"}]}""")

            assertEquals(UpcomingBrief(uid = "e1"), ok(api.upcoming(60)).single())
        }

    @Test
    fun `a server without briefs is not found or unsupported`() =
        runTest {
            answer(404)
            answer(405)

            assertEquals(FailureKind.NotFound, kind(api.upcoming(60)))
            assertEquals(FailureKind.Unsupported, kind(api.upcoming(60)))
        }

    @Test
    fun `an unreachable server is a network failure`() =
        runTest {
            server.close()

            assertTrue(api.upcoming(60) is ApiResult.Failure)
        }
}
