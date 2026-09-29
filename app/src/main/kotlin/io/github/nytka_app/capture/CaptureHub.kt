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
        private val muteLog: MuteLogRecorder?,
    ) {
        /** For screens' tests: no mute log. */
        constructor(settings: SettingsSource) : this(settings, null)

        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status: StateFlow<CaptureStatus> = mutableStatus.asStateFlow()

        private val mutableSync = MutableStateFlow(StorageSyncStatus())

        /** The offline sync, whether or not the service runs. */
        val syncStatus: StateFlow<StorageSyncStatus> = mutableSync.asStateFlow()

        private val controller = MutableStateFlow<CaptureController?>(null)
        private val sync = MutableStateFlow<StorageSyncController?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val captured: Flow<CapturedFrame> = controller.flatMapLatest { it?.captured ?: emptyFlow() }

        fun attach(controller: CaptureController) {
            this.controller.value = controller
        }

        fun attachSync(controller: StorageSyncController) {
            sync.value = controller
        }

        fun detach() {
            controller.value = null
            sync.value = null
            mutableStatus.value = CaptureStatus()
            mutableSync.value = StorageSyncStatus()
        }

        fun publishSync(status: StorageSyncStatus) {
            mutableSync.value = status
        }

        fun syncNow() {
            sync.value?.syncNow()
        }

        fun stopSync() {
            sync.value?.stopSync()
        }

        fun importBacklog() {
            sync.value?.importBacklog()
        }

        fun discardBacklog() {
            sync.value?.discardBacklog()
        }

        fun publish(status: CaptureStatus) {
            mutableStatus.value = status
        }

        suspend fun setMuted(
            muted: Boolean,
            source: MuteSource,
        ) {
            controller.value?.setMuted(muted, source) ?: run {
                // No service: nobody else sees this change, and the sync needs its time for the mute log.
                settings.update { it.copy(muted = muted) }
                muteLog?.record(muted)
            }
        }
    }
