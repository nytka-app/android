package io.github.nytka_app.ui.developer

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeDiagnostics
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.CapturedFrame
import io.github.nytka_app.capture.FixtureRecorder
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DiagnosticsResult
import io.github.nytka_app.core.api.ServerStatus
import io.github.nytka_app.core.api.UploadResult
import io.github.nytka_app.core.diagnostics.DiagnosticsUploader
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.queue.SealedChunk
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.upload.ChunkSource
import io.github.nytka_app.core.upload.Uploader
import io.github.nytka_app.sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeveloperViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private object NoChunks : ChunkSource {
        override val usage = MutableStateFlow(QueueUsage())

        override suspend fun oldest(): SealedChunk? = null

        override suspend fun remove(chunkId: Long) = Unit
    }

    private val settings = FakeSettings(Settings(developerMode = true))
    private val actions = FakeDeviceActions()
    private val fixtures =
        object : FixtureRecorder {
            override suspend fun record(frames: Flow<CapturedFrame>) = "/sdcard/fixtures/frames-1.nytk"
        }

    private val diagnostics = FakeDiagnostics()
    private var diagnosticsAnswer: DiagnosticsResult = DiagnosticsResult.Accepted(1)
    private val diagnosticsUploader = DiagnosticsUploader(diagnostics, { diagnosticsAnswer }, settings)

    private fun viewModel() =
        DeveloperViewModel(
            settings = settings,
            actions = actions,
            hub = CaptureHub(settings),
            queue = NoChunks,
            uploader = Uploader(NoChunks, { UploadResult.Retry("unused") }, settings.settings),
            status = { ApiResult.Ok(ServerStatus(pendingChunks = 3)) },
            fixtures = fixtures,
            diagnostics = diagnostics,
            diagnosticsUploader = diagnosticsUploader,
        )

    @Test
    fun `the fake pendant switch restarts capture`() {
        viewModel().setFakePendant(true)

        assertEquals(true, settings.state.value.fakePendant)
        assertEquals(listOf("restart"), actions.calls)
    }

    @Test
    fun `thresholds are saved`() {
        viewModel().setThresholds(disconnectedMinutes = 1, unreachableMinutes = 2, batteryPercent = 30)

        assertEquals(
            Triple(1, 2, 30),
            settings.state.value.let {
                Triple(it.alertDisconnectedMinutes, it.alertUnreachableMinutes, it.alertBatteryPercent)
            },
        )
    }

    @Test
    fun `developer mode turns off`() {
        viewModel().turnOff()

        assertFalse(settings.state.value.developerMode)
    }

    @Test
    fun `a recorded fixture says where it went`() {
        val viewModel = viewModel()

        viewModel.recordFixture()

        assertEquals("Saved to /sdcard/fixtures/frames-1.nytk", viewModel.state.value.fixture)
    }

    @Test
    fun `the diagnostics switch is off by default and is saved`() {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.diagnosticsUpload)
        viewModel.setDiagnosticsUpload(true)

        assertTrue(settings.state.value.diagnosticsUpload)
        assertTrue(viewModel.state.value.diagnosticsUpload)
        viewModel.setDiagnosticsUpload(false)
        assertFalse(settings.state.value.diagnosticsUpload)
    }

    @Test
    fun `a server without diagnostics turns the switch off with a note until it is switched on again`() =
        runTest {
            diagnostics.add(sample(1))
            diagnosticsAnswer = DiagnosticsResult.NotSupported
            val viewModel = viewModel()
            viewModel.setDiagnosticsUpload(true)

            diagnosticsUploader.flush()

            assertFalse(viewModel.state.value.diagnosticsUpload)
            assertEquals(
                "Your server does not accept diagnostics (needs server 0.2.0)",
                viewModel.state.value.diagnosticsNote,
            )
            viewModel.setDiagnosticsUpload(true)
            assertNull(viewModel.state.value.diagnosticsNote)
        }

    @Test
    fun `export hands the last seven days to the share sheet`() {
        diagnostics.add(sample(1), sample(2))
        val viewModel = viewModel()

        viewModel.exportDiagnostics()

        assertEquals(listOf("share diagnostics"), actions.calls)
        assertEquals(listOf(listOf(sample(1), sample(2))), actions.shared)
        assertEquals("Exported 2 samples from the last 7 days.", viewModel.state.value.diagnosticsExport)
    }

    @Test
    fun `export with nothing recorded says so and shares nothing`() {
        val viewModel = viewModel()

        viewModel.exportDiagnostics()

        assertTrue(actions.calls.isEmpty())
        assertEquals(
            "Nothing to export yet: samples are taken while capture runs.",
            viewModel.state.value.diagnosticsExport,
        )
    }

    @Test
    fun `the state counts the samples and the report says so`() =
        runTest {
            diagnostics.add(sample(1), sample(2), sample(3))
            val viewModel = viewModel()

            assertEquals(3, viewModel.state.value.diagnosticsSamples)
            assertTrue(viewModel.report().lines().contains("diagnostics=3 samples, upload=off"))
        }
}
