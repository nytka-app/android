package io.github.nytka_app.capture

import io.github.nytka_app.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the screens see of capture, and how they mute it, whether or not the service runs. */
@Singleton
class CaptureHub
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status: StateFlow<CaptureStatus> = mutableStatus.asStateFlow()

        @Volatile private var controller: CaptureController? = null

        fun attach(controller: CaptureController) {
            this.controller = controller
        }

        fun detach() {
            controller = null
            mutableStatus.value = CaptureStatus()
        }

        fun publish(status: CaptureStatus) {
            mutableStatus.value = status
        }

        suspend fun setMuted(muted: Boolean) {
            controller?.setMuted(muted) ?: settings.update { it.copy(muted = muted) }
        }
    }
