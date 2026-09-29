package io.github.nytka_app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.BuildConfig
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.capture.SyncControls
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerUrl
import io.github.nytka_app.core.api.UrlCheck
import io.github.nytka_app.core.settings.MuteSchedule
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.ui.firstrun.READ_TOKEN_REFUSED
import io.github.nytka_app.ui.mustAskForLocalNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val pendantName: String? = null,
    val pendantAddress: String? = null,
    val serverUrl: String = "",
    val tokenSet: Boolean = false,
    val privateNetwork: Boolean = false,
    val serverState: String = "Not checked yet",
    val apiVersion: Int? = null,
    val apiMismatch: Boolean = false,
    val saveError: String? = null,
    /** True while the screen should show Android's prompt for the local network. */
    val askLocalNetwork: Boolean = false,
    /**
     * True when the last check failed on the network. On Android 17 that can be the local network permission,
     * whatever the address looks like, so the screen offers to allow it.
     */
    val serverUnreachable: Boolean = false,
    val developerMode: Boolean = false,
    val tapsToDeveloper: Int = DeviceViewModel.TAPS_TO_DEVELOPER,
    val version: String = BuildConfig.VERSION_NAME,
    /** The Pendant storage card; null hides it (no pendant, or a server without offline sync). */
    val storage: StorageCard? = null,
    /** Packets the first sync found, while it waits for "import or discard"; null when nothing is asked. */
    val backlogPackets: Long? = null,
    /** Whether /info lists `offline-sync`; null until it answers and after a failed check. Only true shows the card. */
    val serverSync: Boolean? = null,
    val muteSchedule: MuteSchedule = MuteSchedule(),
)

@HiltViewModel
class DeviceViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val info: InfoClient,
        private val actions: DeviceActions,
        private val sync: SyncControls,
    ) : ViewModel() {
        private val local = MutableStateFlow(DeviceUiState())

        val state: StateFlow<DeviceUiState> =
            combine(settings.settings, local, sync.status, sync.connected) { current, screen, storage, connected ->
                screen.copy(
                    storage =
                        storageCard(storage, current.pendantAddress != null || current.fakePendant, connected)
                            ?.takeIf { screen.serverSync == true },
                    // Nobody can answer while the pendant is away: the sync (and its service) is what takes the answer.
                    backlogPackets = storage.backlogPackets()?.takeIf { connected },
                    pendantName = current.pendantName,
                    pendantAddress = current.pendantAddress,
                    serverUrl = current.serverUrl,
                    tokenSet = current.token.isNotEmpty(),
                    privateNetwork = current.privateNetwork,
                    developerMode = current.developerMode,
                    muteSchedule = current.muteSchedule,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceUiState())

        init {
            checkServer()
        }

        fun checkServer() {
            local.update { it.copy(serverState = "Checking…") }
            viewModelScope.launch {
                val saved = settings.current()
                val base = (ServerUrl.check(saved.serverUrl, saved.privateNetwork) as? UrlCheck.Ok)?.base
                if (base != null && mustAskForLocalNetwork(base, saved.privateNetwork, actions)) {
                    // Android 17 blocks a server on the local network until Nytka may use it. The screen asks, and
                    // localNetworkAnswered() checks the server once the user has answered.
                    local.update { it.copy(askLocalNetwork = true) }
                } else {
                    query()
                }
            }
        }

        /** The check goes on whatever the answer: over a VPN the server is reachable without the permission. */
        fun localNetworkAnswered() {
            local.update { it.copy(askLocalNetwork = false) }
            viewModelScope.launch { query() }
        }

        /** What the settings were before the last Save, until that Save's check has answered. */
        private var beforeSave: Settings? = null

        private suspend fun query() {
            val before = beforeSave
            beforeSave = null
            when (val result = info.info()) {
                is ApiResult.Ok if result.value.scope == ServerInfo.SCOPE_READ -> {
                    // The app edits settings and tokens: a read token is refused, and the old settings come back.
                    before?.let { old -> settings.update { old } }
                    local.update {
                        it.copy(
                            serverState = READ_TOKEN_REFUSED,
                            saveError = if (before != null) READ_TOKEN_REFUSED else it.saveError,
                            apiVersion = null,
                            apiMismatch = false,
                            serverUnreachable = false,
                        )
                    }
                }
                is ApiResult.Ok ->
                    local.update {
                        it.copy(
                            serverState = "Connected to Nytka server ${result.value.serverVersion}",
                            apiVersion = result.value.apiVersion,
                            apiMismatch = result.value.apiVersion != NytkaApi.API_VERSION,
                            serverUnreachable = false,
                            serverSync = result.value.has(ServerInfo.FEATURE_OFFLINE_SYNC),
                        )
                    }
                is ApiResult.Failure ->
                    local.update {
                        it.copy(
                            serverState = result.message,
                            apiVersion = null,
                            apiMismatch = false,
                            serverSync = null,
                            serverUnreachable = result.kind == FailureKind.Network,
                        )
                    }
            }
        }

        /** An empty [token] keeps the stored one. */
        fun save(
            url: String,
            token: String,
            privateNetwork: Boolean,
        ) {
            when (val check = ServerUrl.check(url, privateNetwork)) {
                is UrlCheck.Invalid -> local.update { it.copy(saveError = check.reason) }
                is UrlCheck.Ok ->
                    viewModelScope.launch {
                        beforeSave = settings.current()
                        settings.update {
                            it.copy(
                                serverUrl = check.base.toString(),
                                token = token.trim().ifEmpty { it.token },
                                privateNetwork = privateNetwork,
                            )
                        }
                        local.update { it.copy(saveError = null) }
                        checkServer()
                    }
            }
        }

        /** The capture service watches the setting, so a change needs no restart. */
        fun setMuteSchedule(schedule: MuteSchedule) {
            viewModelScope.launch { settings.update { it.copy(muteSchedule = schedule) } }
        }

        fun syncNow() = sync.syncNow()

        fun stopSync() = sync.stopSync()

        /** The answer to the first-sync question; the default, and what a dismissed question means. */
        fun importBacklog() = sync.importBacklog()

        /** Only after the screen's confirm: the pendant's ring is freed without being read. */
        fun discardBacklog() = sync.discardBacklog()

        fun tapVersion() {
            val left = (local.value.tapsToDeveloper - 1).coerceAtLeast(0)
            local.update { it.copy(tapsToDeveloper = left) }
            if (left == 0) viewModelScope.launch { settings.update { it.copy(developerMode = true) } }
        }

        fun paired(pendant: PairedPendant) {
            viewModelScope.launch {
                settings.update {
                    it.copy(
                        pendantAddress = pendant.address,
                        pendantName = pendant.name,
                        fakePendant = false,
                    )
                }
                actions.restartCapture()
            }
        }

        fun forgetPendant() {
            viewModelScope.launch {
                val address = settings.current().pendantAddress ?: return@launch
                actions.stopCapture()
                actions.forgetPendant(address)
                settings.update { it.copy(pendantAddress = null, pendantName = null) }
            }
        }

        companion object {
            const val TAPS_TO_DEVELOPER = 7
        }
    }
