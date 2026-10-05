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

class VoiceprintsApiTest {
    private val server = MockWebServer()
    private val nytka =
        NytkaApi(OkHttpClient()) { Settings(server.url("/").toString(), "token-1", privateNetwork = true) }
    private val api = VoiceprintsApi(nytka)

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun answer(code: Int) = server.enqueue(MockResponse.Builder().code(code).build())

    @Test
    fun `deleteAll sends DELETE to the voiceprints route and accepts 204`() =
        runTest {
            answer(204)

            val result = api.deleteAll()

            assertTrue(result is ApiResult.Ok)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/people/voiceprints", request.url.encodedPath)
            assertEquals("Bearer token-1", request.headers["Authorization"])
        }

    @Test
    fun `an older server and a read token are told apart`() =
        runTest {
            answer(404)
            answer(405)
            answer(403)

            assertEquals(FailureKind.NotFound, (api.deleteAll() as ApiResult.Failure).kind)
            assertEquals(FailureKind.Unsupported, (api.deleteAll() as ApiResult.Failure).kind)
            assertEquals(FailureKind.Forbidden, (api.deleteAll() as ApiResult.Failure).kind)
        }
}
