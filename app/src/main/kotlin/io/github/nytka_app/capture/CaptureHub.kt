package io.github.nytka_app.capture

import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.pendant.AudioFrame
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

        private val mutableSettings = MutableStateFlow(PendantSettingsState())

        /** The pendant's LED and microphone settings, whether or not the service runs. */
        val settingsState: StateFlow<PendantSettingsState> = mutableSettings.asStateFlow()

        private val controller = MutableStateFlow<CaptureController?>(null)
        private val sync = MutableStateFlow<StorageSyncController?>(null)
        private val pendantSettings = MutableStateFlow<PendantSettingsControls?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val captured: Flow<CapturedFrame> = controller.flatMapLatest { it?.captured ?: emptyFlow() }

        /** Voice enrollment's taker of live frames; kept here so a service that restarts meanwhile diverts too. */
        @Volatile
        private var diversion: ((AudioFrame) -> Unit)? = null

        fun attach(controller: CaptureController) {
            controller.diversion = diversion
            this.controller.value = controller
        }

        /** Live frames go to [sink] instead of the upload queue; false, diverting nothing, without capture. */
        fun divert(sink: (AudioFrame) -> Unit): Boolean {
            val running = controller.value ?: return false
            diversion = sink
            running.diversion = sink
            return true
        }

        /** Live frames go to the queue again. */
        fun undivert() {
            diversion = null
            controller.value?.diversion = null
        }

        fun attachSync(controller: StorageSyncController) {
            sync.value = controller
        }

        fun attachSettings(controls: PendantSettingsControls) {
            pendantSettings.value = controls
        }

        fun detach() {
            controller.value = null
            sync.value = null
            pendantSettings.value = null
            mutableSettings.value = PendantSettingsState()
            mutableStatus.value = CaptureStatus()
            mutableSync.value = StorageSyncStatus()
        }

        fun publishSync(status: StorageSyncStatus) {
            mutableSync.value = status
        }

        fun publishSettings(state: PendantSettingsState) {
            mutableSettings.value = state
        }

        fun commitLed(percent: Int) {
            pendantSettings.value?.commitLed(percent)
        }

        fun commitGain(level: Int) {
            pendantSettings.value?.commitGain(level)
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
