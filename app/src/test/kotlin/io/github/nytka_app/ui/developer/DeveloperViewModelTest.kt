package io.github.nytka_app.ui.developer

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.CapturedFrame
import io.github.nytka_app.capture.FixtureRecorder
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ServerStatus
import io.github.nytka_app.core.api.UploadResult
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.queue.SealedChunk
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.upload.ChunkSource
import io.github.nytka_app.core.upload.Uploader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun viewModel() =
        DeveloperViewModel(
            settings = settings,
            actions = actions,
            hub = CaptureHub(settings),
            queue = NoChunks,
            uploader = Uploader(NoChunks, { UploadResult.Retry("unused") }, settings.settings),
            status = { ApiResult.Ok(ServerStatus(pendingChunks = 3)) },
            fixtures = fixtures,
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
}
