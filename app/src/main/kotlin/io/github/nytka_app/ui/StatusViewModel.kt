package io.github.nytka_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.CaptureStatus
import io.github.nytka_app.capture.MuteSource
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.UploadState
import io.github.nytka_app.core.upload.Uploader
import io.github.nytka_app.ui.conversations.Formatting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class StatusUiState(
    val capture: CaptureStatus = CaptureStatus(),
    val muted: Boolean = false,
    val serverLine: String = "",
    val queuedChunks: Int = 0,
    val developerMode: Boolean = false,
    /** Uploads fail on the network: on Android 17 that can be the local network permission, so the card offers it. */
    val serverUnreachable: Boolean = false,
)

/** The one line the status card shows about the server, most useful first. */
fun serverLine(
    upload: UploadState,
    zone: ZoneId,
): String {
    fun at(ms: Long) = Formatting.clock(Instant.ofEpochMilli(ms), zone)
    // UploadState lives in :core, so its nullable properties cannot be smart-cast here.
    upload.paused?.let { return "Paused: $it" }
    upload.unreachableSinceMs?.let { return "Unreachable since ${at(it)}" }
    upload.lastUploadAtMs?.let { return "Last upload ${at(it)}" }
    return "Waiting for the first upload"
}

/** Uploads fail on the network, as against a server that answers with a refusal. */
fun uploadsUnreachable(upload: UploadState) = upload.paused == null && upload.unreachableSinceMs != null

/** Capture, mute and server state for the top bar, the status card and the Device tab. */
@HiltViewModel
class StatusViewModel
    @Inject
    constructor(
        private val hub: CaptureHub,
        settings: SettingsStore,
        queue: FrameQueue,
        uploader: Uploader,
        clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<StatusUiState> =
            combine(hub.status, settings.settings, queue.usage, uploader.state) { capture, current, usage, upload ->
                StatusUiState(
                    capture,
                    current.muted,
                    serverLine(upload, clock.zone),
                    usage.chunks,
                    current.developerMode,
                    uploadsUnreachable(upload),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), StatusUiState())

        init {
            // The queue reports its size after the first seal; ask once so the card is right at launch.
            viewModelScope.launch { queue.refreshUsage() }
        }

        fun setMuted(muted: Boolean) {
            viewModelScope.launch { hub.setMuted(muted, MuteSource.App) }
        }

        private companion object {
            const val STOP_AFTER_MS = 5_000L
        }
    }
