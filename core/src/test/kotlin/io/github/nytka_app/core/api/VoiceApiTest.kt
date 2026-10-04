package io.github.nytka_app.core.api

import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The voice calls against the contract of server 0.12 (docs/specs/your-voice.md). */
class VoiceApiTest {
    private val server = MockWebServer()
    private val api =
        VoiceApi(NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) })

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

    private fun problem(
        reason: String,
        speech: Double,
        samples: Int,
    ) = """{"type":"about:blank","title":"Some words.","status":422,"reason":"$reason","speechSeconds":$speech,
        "samples":$samples,"minAgreement":0.31}"""

    private val body = byteArrayOf(1, 2, 3)

    @Test
    fun `voice decodes the status`() =
        runTest {
            answer(
                200,
                """{"enrolled":true,"enrolledAt":"2026-10-04T08:00:00Z","updatedAt":"2026-10-04T09:00:00Z",
                "enrolledSamples":9,"learnedSegments":4,"modelAvailable":true}""",
            )

            val status = (api.voice() as ApiResult.Ok).value

            assertEquals(VoiceStatus(true, "2026-10-04T08:00:00Z", "2026-10-04T09:00:00Z", 9, 4, true), status)
            assertEquals("/api/v1/voice", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `an enrollment posts the frames with the mode and reads the numbers`() =
        runTest {
            answer(200, """{"speechSeconds":31.5,"samples":7,"minAgreement":0.62}""")

            val result = api.enroll(body, EnrollMode.Add)

            assertEquals(EnrollResult.Enrolled(31.5, 7, 0.62), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/voice/enrollment", request.url.encodedPath)
            assertEquals("add", request.url.queryParameter("mode"))
            assertEquals(ChunkFormat.MEDIA_TYPE, request.headers["Content-Type"])
            assertEquals("Bearer token-1", request.headers["Authorization"])
            assertArrayEquals(body, request.body?.toByteArray())
        }

    @Test
    fun `replace is the mode by name`() =
        runTest {
            answer(200, """{"speechSeconds":30,"samples":6,"minAgreement":0.6}""")

            api.enroll(body, EnrollMode.Replace)

            assertEquals("replace", server.takeRequest().url.queryParameter("mode"))
        }

    @Test
    fun `too little speech is its own refusal`() =
        runTest {
            answer(422, problem("too-little-speech", 12.4, 2))

            assertEquals(
                EnrollResult.Refused(EnrollRefusal.TooLittleSpeech, 12.4, 2),
                api.enroll(body, EnrollMode.Replace),
            )
        }

    @Test
    fun `too few samples is its own refusal`() =
        runTest {
            answer(422, problem("too-few-samples", 21.0, 2))

            assertEquals(
                EnrollResult.Refused(EnrollRefusal.TooFewSamples, 21.0, 2),
                api.enroll(body, EnrollMode.Replace),
            )
        }

    @Test
    fun `samples that disagree are their own refusal`() =
        runTest {
            answer(422, problem("samples-disagree", 40.0, 8))

            assertEquals(
                EnrollResult.Refused(EnrollRefusal.SamplesDisagree, 40.0, 8),
                api.enroll(body, EnrollMode.Replace),
            )
        }

    @Test
    fun `a reason this app does not know is still a refusal`() =
        runTest {
            answer(422, problem("too-quiet", 25.0, 4))

            assertEquals(EnrollResult.Refused(null, 25.0, 4), api.enroll(body, EnrollMode.Replace))
        }

    @Test
    fun `413 is too long and 503 is no model`() =
        runTest {
            answer(413, """{"title":"An enrollment may not exceed 120 seconds."}""")
            answer(503, """{"title":"The speaker model is not installed."}""")

            assertEquals(EnrollResult.TooLong, api.enroll(body, EnrollMode.Replace))
            assertEquals(EnrollResult.NoModel, api.enroll(body, EnrollMode.Replace))
        }

    @Test
    fun `400 and 415 are unreadable audio and 409 another model`() =
        runTest {
            answer(400, """{"title":"Unreadable audio.","detail":"Bad magic."}""")
            answer(415)
            answer(409)

            assertEquals(EnrollResult.Unreadable, api.enroll(body, EnrollMode.Replace))
            assertEquals(EnrollResult.Unreadable, api.enroll(body, EnrollMode.Replace))
            assertEquals(EnrollResult.OtherModel, api.enroll(body, EnrollMode.Add))
        }

    @Test
    fun `a read token and an older server fail as other calls do`() =
        runTest {
            answer(403)
            answer(404)

            assertEquals(
                FailureKind.Forbidden,
                (api.enroll(body, EnrollMode.Replace) as EnrollResult.Failed).failure.kind,
            )
            assertEquals(
                FailureKind.NotFound,
                (api.enroll(body, EnrollMode.Replace) as EnrollResult.Failed).failure.kind,
            )
        }

    @Test
    fun `reset posts and returns the status, forget deletes`() =
        runTest {
            answer(200, """{"enrolled":true,"enrolledSamples":9,"learnedSegments":0,"modelAvailable":true}""")
            answer(204)

            assertEquals(0, (api.reset() as ApiResult.Ok).value.learnedSegments)
            assertEquals(ApiResult.Ok(Unit), api.forget())

            val reset = server.takeRequest()
            assertEquals("POST", reset.method)
            assertEquals("/api/v1/voice/reset", reset.url.encodedPath)
            val forget = server.takeRequest()
            assertEquals("DELETE", forget.method)
            assertEquals("/api/v1/voice", forget.url.encodedPath)
        }
}
