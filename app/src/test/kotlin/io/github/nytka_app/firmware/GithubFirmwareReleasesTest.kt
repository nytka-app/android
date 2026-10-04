package io.github.nytka_app.firmware

import io.github.nytka_app.pendant.FirmwareStream
import io.github.nytka_app.pendant.FirmwareVersion
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GithubFirmwareReleasesTest {
    private val server = MockWebServer()
    private lateinit var releases: GithubFirmwareReleases

    @Before
    fun start() {
        server.start()
        releases = GithubFirmwareReleases(OkHttpClient(), server.url("/"))
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

    private val refs =
        """[{"ref":"refs/tags/Omi_CV1_v3.0.9"},{"ref":"refs/tags/Omi_CV1_v3.0.7_pre"},
           |{"ref":"refs/tags/Omi_CV1_v3.0.21"},{"ref":"refs/tags/Omi_CV1_v3.0.20"}]
        """.trimMargin()

    @Test
    fun `picks the highest tag by number, not by text, and checks its release`() =
        runTest {
            answer(200, refs)
            answer(200, """{"draft":false,"prerelease":false,"tag_name":"Omi_CV1_v3.0.21"}""")

            val result = releases.latest(FirmwareStream.OmiCv1)

            assertEquals(ReleaseAnswer.Latest(FirmwareVersion(3, 0, 21)), result)
            val first = server.takeRequest()
            assertEquals("/repos/BasedHardware/omi/git/matching-refs/tags/Omi_CV1_v?per_page=100", first.target)
            assertEquals("/repos/BasedHardware/omi/releases/tags/Omi_CV1_v3.0.21", server.takeRequest().target)
        }

    @Test
    fun `skips a pre-release and falls to the next published one`() =
        runTest {
            answer(200, refs)
            answer(200, """{"draft":false,"prerelease":true}""")
            answer(200, """{"draft":false,"prerelease":false}""")

            assertEquals(ReleaseAnswer.Latest(FirmwareVersion(3, 0, 20)), releases.latest(FirmwareStream.OmiCv1))
        }

    @Test
    fun `gives up after three release lookups`() =
        runTest {
            answer(200, refs)
            repeat(3) { answer(404) }

            assertEquals(ReleaseAnswer.NoRelease, releases.latest(FirmwareStream.OmiCv1))
            assertEquals(4, server.requestCount)
        }

    @Test
    fun `a rate limit or a server error is no release`() =
        runTest {
            answer(403, """{"message":"API rate limit exceeded"}""")

            assertEquals(ReleaseAnswer.NoRelease, releases.latest(FirmwareStream.OmiCv1))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `an answer that is not json is no release`() =
        runTest {
            answer(200, "<html>captive portal</html>")

            assertEquals(ReleaseAnswer.NoRelease, releases.latest(FirmwareStream.OmiCv1))
        }

    @Test
    fun `no network is unreachable`() =
        runTest {
            server.close()

            assertEquals(ReleaseAnswer.Unreachable, releases.latest(FirmwareStream.OmiCv1))
        }

    @Test
    fun `sends no cookie, token or identifier`() =
        runTest {
            answer(200, "[]")

            releases.latest(FirmwareStream.OmiCv1)

            val headers = server.takeRequest().headers
            assertEquals(null, headers["Authorization"])
            assertEquals(null, headers["Cookie"])
        }
}
