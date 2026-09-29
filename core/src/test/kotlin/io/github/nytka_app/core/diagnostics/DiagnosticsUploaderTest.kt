package io.github.nytka_app.core.diagnostics

import io.github.nytka_app.core.api.DiagnosticsResult
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsUploaderTest {
    private class FakeSource(
        samples: List<DiagnosticSample>,
    ) : DiagnosticsSource {
        val rows =
            samples
                .map {
                    DiagnosticRow(it.id, 0, Json.encodeToString(DiagnosticSample.serializer(), it))
                }.toMutableList()
        override val count: Flow<Int> = emptyFlow()

        override suspend fun pending(limit: Int) = rows.filter { !it.uploaded }.take(limit)

        override suspend fun markUploaded(ids: List<String>) {
            rows.replaceAll { if (it.id in ids) it.copy(uploaded = true) else it }
        }

        override suspend fun recent(): List<DiagnosticSample> = emptyList()

        val notUploaded get() = rows.count { !it.uploaded }
    }

    private class Settings0(
        initial: Settings,
    ) : io.github.nytka_app.core.settings.SettingsSource {
        val state = MutableStateFlow(initial)
        override val settings = state

        override suspend fun current() = state.value

        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private val settings = Settings0(Settings(diagnosticsUpload = true))
    private val bodies = mutableListOf<String>()
    private var answer: (String) -> DiagnosticsResult = { DiagnosticsResult.Accepted(1) }

    private fun uploader(source: DiagnosticsSource) =
        DiagnosticsUploader(source, { body ->
            bodies += body
            answer(body)
        }, settings)

    private fun samples(count: Int) = List(count) { sample(it) }

    private fun sizes() = bodies.map { Json.parseToJsonElement(it).jsonArray.size }

    @Test
    fun `sends pages of 500 and marks each uploaded on 200`() =
        runTest {
            val source = FakeSource(samples(1_200))

            assertEquals(1_200, uploader(source).flush())

            assertEquals(listOf(500, 500, 200), sizes())
            assertEquals(0, source.notUploaded)
        }

    @Test
    fun `the body is the stored json in order`() =
        runTest {
            val source = FakeSource(samples(2))

            uploader(source).flush()

            assertEquals(
                Json.encodeToString(DiagnosticSample.serializer(), sample(0)),
                Json.parseToJsonElement(bodies.single()).jsonArray[0].toString(),
            )
        }

    @Test
    fun `nothing is sent while the switch is off`() =
        runTest {
            settings.state.value = Settings(diagnosticsUpload = false)
            val source = FakeSource(samples(3))

            assertEquals(0, uploader(source).flush())

            assertTrue(bodies.isEmpty())
            assertEquals(3, source.notUploaded)
        }

    @Test
    fun `turning the switch off stops after the page in flight`() =
        runTest {
            val source = FakeSource(samples(1_200))
            answer = {
                settings.state.value = Settings(diagnosticsUpload = false)
                DiagnosticsResult.Accepted(500)
            }

            uploader(source).flush()

            assertEquals(listOf(500), sizes())
        }

    @Test
    fun `a 404 turns the switch off with a note and keeps the samples`() =
        runTest {
            val source = FakeSource(samples(3))
            answer = { DiagnosticsResult.NotSupported }
            val uploader = uploader(source)

            uploader.flush()

            assertEquals(false, settings.state.value.diagnosticsUpload)
            assertEquals("Your server does not accept diagnostics (needs server 0.2.0)", uploader.note.value)
            assertEquals(3, source.notUploaded)
            uploader.clearNote()
            assertNull(uploader.note.value)
            uploader.flush()
            assertEquals(1, bodies.size)
        }

    @Test
    fun `other failures keep the switch on and retry the same samples next round`() =
        runTest {
            val source = FakeSource(samples(3))
            val uploader = uploader(source)
            listOf(
                DiagnosticsResult.Retry("The server answered 503."),
                DiagnosticsResult.Unauthorized,
                DiagnosticsResult.NotConfigured("No token is set."),
            ).forEach { failure ->
                answer = { failure }
                uploader.flush()
            }

            assertEquals(true, settings.state.value.diagnosticsUpload)
            assertNull(uploader.note.value)
            assertEquals(3, source.notUploaded)

            answer = { DiagnosticsResult.Accepted(3) }
            uploader.flush()

            assertEquals(0, source.notUploaded)
            assertEquals(4, bodies.size)
            assertEquals(1, bodies.toSet().size)
        }

    @Test
    fun `a page stays under the server's 256 KiB limit`() =
        runTest {
            val source = FakeSource(List(500) { sample(it, device = "d".repeat(700)) })

            uploader(source).flush()

            assertTrue(bodies.all { it.toByteArray().size <= 256 * 1024 })
            assertEquals(0, source.notUploaded)
            assertTrue(bodies.size > 1)
        }
}
