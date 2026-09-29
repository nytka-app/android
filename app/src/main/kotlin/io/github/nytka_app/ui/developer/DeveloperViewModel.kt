package io.github.nytka_app.ui.developer

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.BuildConfig
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.FixtureRecorder
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.core.upload.ChunkSource
import io.github.nytka_app.core.upload.Uploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeveloperUiState(
    val packetsPerSecond: Double = 0.0,
    val lossPercent: Double = 0.0,
    val droppedFrames: Long = 0,
    val framesThisSession: Long = 0,
    val queueMegabytes: Double = 0.0,
    val sealedChunks: Int = 0,
    val unsealedFrames: Int = 0,
    val droppedChunks: Long = 0,
    val uploadedChunks: Long = 0,
    val lastUpload: String? = null,
    val serverStatus: String = "Not checked",
    val fakePendant: Boolean = false,
    val fixture: String? = null,
    val disconnectedMinutes: Int = 5,
    val unreachableMinutes: Int = 15,
    val batteryPercent: Int = 20,
)

@HiltViewModel
class DeveloperViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val actions: DeviceActions,
        private val hub: CaptureHub,
        private val queue: ChunkSource,
        private val uploader: Uploader,
        private val status: StatusClient,
        private val fixtures: FixtureRecorder,
    ) : ViewModel() {
        private data class Local(
            val serverStatus: String = "Not checked",
            val fixture: String? = null,
        )

        private val meter = RateMeter()
        private val local = MutableStateFlow(Local())

        val state: StateFlow<DeveloperUiState> =
            combine(
                hub.status,
                queue.usage,
                uploader.state,
                settings.settings,
                local,
            ) { capture, usage, upload, current, screen ->
                DeveloperUiState(
                    packetsPerSecond = meter.sample(capture.stats.notifications, System.currentTimeMillis()),
                    lossPercent = capture.stats.lossFraction * 100,
                    droppedFrames = capture.stats.droppedFrames,
                    framesThisSession = capture.framesQueued,
                    queueMegabytes = usage.bytes / BYTES_PER_MEGABYTE,
                    sealedChunks = usage.chunks,
                    unsealedFrames = usage.frames,
                    droppedChunks = usage.droppedChunks,
                    uploadedChunks = upload.uploadedChunks,
                    lastUpload = upload.lastResult,
                    serverStatus = screen.serverStatus,
                    fakePendant = current.fakePendant,
                    fixture = screen.fixture,
                    disconnectedMinutes = current.alertDisconnectedMinutes,
                    unreachableMinutes = current.alertUnreachableMinutes,
                    batteryPercent = current.alertBatteryPercent,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DeveloperUiState())

        init {
            refreshServerStatus()
        }

        fun refreshServerStatus() {
            viewModelScope.launch {
                val text =
                    when (val result = status.status()) {
                        is ApiResult.Ok ->
                            with(result.value) {
                                "$pendingChunks pending" + (oldestPendingAt?.let { ", oldest from $it" } ?: "") +
                                    ", last error: ${lastError ?: "none"}"
                            }
                        is ApiResult.Failure -> result.message
                    }
                local.update { it.copy(serverStatus = text) }
            }
        }

        fun setFakePendant(on: Boolean) {
            viewModelScope.launch {
                settings.update { it.copy(fakePendant = on) }
                actions.restartCapture()
            }
        }

        fun recordFixture() {
            local.update { it.copy(fixture = "Recording the next 60 seconds…") }
            viewModelScope.launch {
                val outcome = runCatching { fixtures.record(hub.captured) }
                local.update { state ->
                    state.copy(
                        fixture =
                            outcome.fold({ "Saved to $it" }, {
                                it.message
                                    ?: "Recording failed."
                            }),
                    )
                }
            }
        }

        fun setThresholds(
            disconnectedMinutes: Int,
            unreachableMinutes: Int,
            batteryPercent: Int,
        ) {
            viewModelScope.launch {
                settings.update {
                    it.copy(
                        alertDisconnectedMinutes = disconnectedMinutes.coerceAtLeast(1),
                        alertUnreachableMinutes = unreachableMinutes.coerceAtLeast(1),
                        alertBatteryPercent = batteryPercent.coerceIn(1, 99),
                    )
                }
            }
        }

        fun turnOff() {
            viewModelScope.launch {
                val wasFake = settings.current().fakePendant
                settings.update { it.copy(developerMode = false, fakePendant = false) }
                if (wasFake) actions.restartCapture()
            }
        }

        suspend fun report(): String =
            DebugReport.build(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
                android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                settings = settings.current(),
                capture = hub.status.value,
                upload = uploader.state.value,
                usage = queue.usage.value,
                serverStatus = state.value.serverStatus,
            )

        private companion object {
            const val BYTES_PER_MEGABYTE = 1_048_576.0
        }
    }
