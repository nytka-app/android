package io.github.nytka_app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.capture.EnrollmentCapture
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.EnrollMode
import io.github.nytka_app.core.api.EnrollRefusal
import io.github.nytka_app.core.api.EnrollResult
import io.github.nytka_app.core.api.VoiceClient
import io.github.nytka_app.core.api.VoiceStatus
import io.github.nytka_app.core.chunks.EnrollmentRecording
import io.github.nytka_app.pendant.AudioFrame
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The languages the screen has prompts for; the wearer reads every one they speak. */
enum class PromptLanguage {
    Ukrainian,
    Russian,
    English,
}

enum class VoicePhase {
    Idle,

    /** Live frames go to the enrollment, not the upload queue. */
    Recording,

    /** A reading, a reset or a forget is on its way to the server. */
    Sending,
}

/** What the last action came to; the screen words it. */
sealed interface VoiceNotice {
    data class Enrolled(
        val speechSeconds: Double,
        val samples: Int,
    ) : VoiceNotice

    /** A `422`; [refusal] null is a reason this app does not know. */
    data class Refused(
        val refusal: EnrollRefusal?,
        val speechSeconds: Double,
    ) : VoiceNotice

    data object TooLong : VoiceNotice

    data object Unreadable : VoiceNotice

    data object OtherModel : VoiceNotice

    data object NoModel : VoiceNotice

    /** The pendant is not connected, capture does not run, or it is muted. */
    data object NotRecording : VoiceNotice

    data object NothingRecorded : VoiceNotice

    data object Forgotten : VoiceNotice

    data object Reset : VoiceNotice

    /** A fixed sentence for any other failure. */
    data class Failed(
        val message: String,
    ) : VoiceNotice
}

data class VoiceUiState(
    val loading: Boolean = true,
    val status: VoiceStatus? = null,
    /** Why the status could not be read. */
    val error: String? = null,
    val languages: Set<PromptLanguage> = emptySet(),
    val phase: VoicePhase = VoicePhase.Idle,
    val mode: EnrollMode = EnrollMode.Replace,
    /** Seconds of pendant audio in the current reading, against [EnrollmentRecording.MAX_SECONDS]. */
    val seconds: Double = 0.0,
    /** 0 to 1, from the last frames' size. */
    val level: Float = 0f,
    /** The reading reached the server's 120 s cap and stopped taking frames. */
    val full: Boolean = false,
    val pendantReady: Boolean = false,
    val notice: VoiceNotice? = null,
    val confirmForget: Boolean = false,
)

/**
 * Voice enrollment (docs/specs/your-voice.md in nytka-app/server). While a reading records, live frames go to it and
 * never to the upload queue; capture goes back to the queue when the reading is sent, cancelled, full, or the screen
 * goes away, before the server is asked anything.
 */
@HiltViewModel
class VoiceViewModel
    @Inject
    constructor(
        private val api: VoiceClient,
        private val capture: EnrollmentCapture,
    ) : ViewModel() {
        private val local = MutableStateFlow(VoiceUiState())

        val state: StateFlow<VoiceUiState> =
            combine(local, capture.recording) { screen, ready -> screen.copy(pendantReady = ready) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceUiState())

        /** The reading being recorded; null outside [VoicePhase.Recording]. */
        @Volatile
        private var recording: EnrollmentRecording? = null

        init {
            refresh()
        }

        fun refresh() {
            local.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = api.voice()) {
                    is ApiResult.Ok -> local.update { it.copy(loading = false, status = result.value) }
                    is ApiResult.Failure -> local.update { it.copy(loading = false, error = result.notice()) }
                }
            }
        }

        fun toggleLanguage(language: PromptLanguage) =
            local.update {
                it.copy(languages = if (language in it.languages) it.languages - language else it.languages + language)
            }

        fun start(mode: EnrollMode) {
            if (local.value.phase != VoicePhase.Idle || local.value.languages.isEmpty()) return
            if (!capture.recording.value) return local.update { it.copy(notice = VoiceNotice.NotRecording) }
            val reading = EnrollmentRecording()
            recording = reading
            if (!capture.divert { frame -> take(reading, frame) }) {
                recording = null
                return local.update { it.copy(notice = VoiceNotice.NotRecording) }
            }
            local.update {
                it.copy(
                    phase = VoicePhase.Recording,
                    mode = mode,
                    seconds = 0.0,
                    level = 0f,
                    full = false,
                    notice = null,
                )
            }
        }

        /** On the capture thread. A frame past the cap ends the diversion. */
        private fun take(
            reading: EnrollmentRecording,
            frame: AudioFrame,
        ) {
            if (recording !== reading) return
            val kept = reading.add(frame.payload, frame.capturedAtMs)
            if (!kept || reading.full) capture.resume()
            if (!kept || reading.full || reading.frameCount % UPDATE_EVERY == 0) {
                local.update { it.copy(seconds = reading.seconds, level = reading.level(), full = reading.full) }
            }
        }

        fun send() {
            val reading = recording ?: return
            stopRecording()
            if (reading.frameCount == 0) {
                return local.update { it.copy(phase = VoicePhase.Idle, notice = VoiceNotice.NothingRecorded) }
            }
            val mode = local.value.mode
            local.update { it.copy(phase = VoicePhase.Sending, seconds = reading.seconds, level = 0f) }
            viewModelScope.launch {
                val notice =
                    try {
                        when (val result = api.enroll(reading.body(), mode)) {
                            is EnrollResult.Enrolled -> VoiceNotice.Enrolled(result.speechSeconds, result.samples)
                            is EnrollResult.Refused -> VoiceNotice.Refused(result.refusal, result.speechSeconds)
                            EnrollResult.TooLong -> VoiceNotice.TooLong
                            EnrollResult.Unreadable -> VoiceNotice.Unreadable
                            EnrollResult.OtherModel -> VoiceNotice.OtherModel
                            EnrollResult.NoModel -> VoiceNotice.NoModel
                            is EnrollResult.Failed -> VoiceNotice.Failed(result.failure.notice())
                        }
                    } finally {
                        local.update { it.copy(phase = VoicePhase.Idle) }
                    }
                local.update { it.copy(notice = notice) }
                if (notice is VoiceNotice.Enrolled) refresh()
            }
        }

        fun cancel() {
            if (local.value.phase != VoicePhase.Recording) return
            stopRecording()
            local.update { it.copy(phase = VoicePhase.Idle, seconds = 0.0, level = 0f, full = false) }
        }

        fun askForget() = local.update { it.copy(confirmForget = true) }

        fun dismissForget() = local.update { it.copy(confirmForget = false) }

        fun forget() {
            local.update { it.copy(confirmForget = false, phase = VoicePhase.Sending) }
            viewModelScope.launch {
                val result = api.forget()
                local.update {
                    it.copy(
                        phase = VoicePhase.Idle,
                        notice =
                            if (result is ApiResult.Failure) {
                                VoiceNotice.Failed(
                                    result.notice(),
                                )
                            } else {
                                VoiceNotice.Forgotten
                            },
                    )
                }
                if (result is ApiResult.Ok) refresh()
            }
        }

        /** Back to the enrolled voiceprint, forgetting what Nytka learned since. */
        fun reset() {
            local.update { it.copy(phase = VoicePhase.Sending) }
            viewModelScope.launch {
                when (val result = api.reset()) {
                    is ApiResult.Ok ->
                        local.update {
                            it.copy(
                                phase = VoicePhase.Idle,
                                status = result.value,
                                notice = VoiceNotice.Reset,
                            )
                        }

                    is ApiResult.Failure ->
                        local.update { it.copy(phase = VoicePhase.Idle, notice = VoiceNotice.Failed(result.notice())) }
                }
            }
        }

        private fun stopRecording() {
            recording = null
            capture.resume()
        }

        /** Leaving the screen mid-reading must not keep live audio out of the queue. */
        override fun onCleared() {
            if (recording != null) stopRecording()
        }

        private companion object {
            /** Five frames, 100 ms: often enough for the meter, not a state change per frame. */
            const val UPDATE_EVERY = 5
        }
    }
