package io.github.nytka_app.capture

import io.github.nytka_app.core.settings.SettingsSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** What the screens see of capture, and how they mute it, whether or not the service runs. */
@Singleton
class CaptureHub
    @Inject
    constructor(
        private val settings: SettingsSource,
    ) {
        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status: StateFlow<CaptureStatus> = mutableStatus.asStateFlow()

        private val controller = MutableStateFlow<CaptureController?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val captured: Flow<CapturedFrame> = controller.flatMapLatest { it?.captured ?: emptyFlow() }

        fun attach(controller: CaptureController) {
            this.controller.value = controller
        }

        fun detach() {
            controller.value = null
            mutableStatus.value = CaptureStatus()
        }

        fun publish(status: CaptureStatus) {
            mutableStatus.value = status
        }

        suspend fun setMuted(muted: Boolean) {
            controller.value?.setMuted(muted) ?: settings.update { it.copy(muted = muted) }
        }
    }
