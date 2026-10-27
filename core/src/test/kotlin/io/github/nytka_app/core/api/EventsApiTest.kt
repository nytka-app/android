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

class EventsApiTest {
    private val server = MockWebServer()
    private val events =
        EventsApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) })

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

    @Test
    fun `events sends the date and the zone and decodes every field`() =
        runTest {
            answer(
                200,
                """{"date":"2026-09-29","events":[
                {"id":"e1","kind":"person_joined","at":"2026-09-29T09:37:00Z","conversationId":"c1",
                "personId":"p1","voiceLabel":"Olena","detail":{},"source":"segments","extra":1},
                {"id":"e2","kind":"silence","at":"2026-09-29T09:02:00Z","conversationId":null,"personId":null,
                "voiceLabel":null,"detail":{"minutes":30},"source":"segments"}]}""",
            )

            val day = (events.events("2026-09-29", "Europe/Kyiv") as ApiResult.Ok).value

            val request = server.takeRequest()
            assertEquals("/api/v1/events", request.url.encodedPath)
            assertEquals("2026-09-29", request.url.queryParameter("date"))
            assertEquals("Europe/Kyiv", request.url.queryParameter("tz"))
            assertEquals("2026-09-29", day.date)
            val joined = day.events[0]
            assertEquals(DayEvent.PERSON_JOINED, joined.kind)
            assertEquals("c1", joined.conversationId)
            assertEquals("p1", joined.personId)
            assertEquals("Olena", joined.voiceLabel)
            assertEquals("segments", joined.source)
            assertEquals(30, day.events[1].minutes)
        }

    @Test
    fun `detail reads as an object, as a json string and when absent or odd`() {
        fun event(detail: String?) =
            DayEvent(
                "e",
                DayEvent.MEDIA_STARTED,
                "2026-09-29T10:00:00Z",
                detail =
                    detail?.let {
                        kotlinx.serialization.json.Json
                            .parseToJsonElement(it)
                    },
            )

        assertEquals("video", event("""{"category":"video"}""").category)
        assertEquals("video", event("\"{\\\"category\\\": \\\"video\\\"}\"").category)
        assertNull(event(null).category)
        assertNull(event("[1,2]").category)
        assertNull(event("\"not json\"").category)
        assertNull(event("""{"category":""}""").category)
        assertNull(event("""{"minutes":"x"}""").minutes)
    }

    @Test
    fun `a bad zone is a plain failure and a server without the route is not found`() =
        runTest {
            answer(400)
            answer(404)

            assertEquals(FailureKind.Server, (events.events("2026-09-29", "Mars/Base") as ApiResult.Failure).kind)
            assertEquals(FailureKind.NotFound, (events.events("2026-09-29", "UTC") as ApiResult.Failure).kind)
        }
}
