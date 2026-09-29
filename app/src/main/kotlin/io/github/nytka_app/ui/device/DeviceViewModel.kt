package io.github.nytka_app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.BuildConfig
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.ServerUrl
import io.github.nytka_app.core.api.UrlCheck
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.ui.PermissionAnswer
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
    /** How the user last answered that prompt while the server was out of reach. */
    val localNetwork: PermissionAnswer? = null,
    val developerMode: Boolean = false,
    val tapsToDeveloper: Int = DeviceViewModel.TAPS_TO_DEVELOPER,
    val version: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class DeviceViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val info: InfoClient,
        private val actions: DeviceActions,
    ) : ViewModel() {
        private val local = MutableStateFlow(DeviceUiState())

        val state: StateFlow<DeviceUiState> =
            combine(settings.settings, local) { current, screen ->
                screen.copy(
                    pendantName = current.pendantName,
                    pendantAddress = current.pendantAddress,
                    serverUrl = current.serverUrl,
                    tokenSet = current.token.isNotEmpty(),
                    privateNetwork = current.privateNetwork,
                    developerMode = current.developerMode,
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
                val ask =
                    base != null &&
                        mustAskForLocalNetwork(base, saved.privateNetwork, local.value.localNetwork, actions)
                if (ask) {
                    // Android 17 blocks a server on the local network until Nytka may use it. The screen asks, and
                    // localNetworkAnswered() checks the server once the user has answered.
                    local.update { it.copy(askLocalNetwork = true) }
                } else {
                    if (actions.localNetworkGranted()) local.update { it.copy(localNetwork = null) }
                    query()
                }
            }
        }

        /** The check goes on whatever the answer: over a VPN the server is reachable without the permission. */
        fun localNetworkAnswered(answer: PermissionAnswer) {
            val refused = answer.takeUnless { it == PermissionAnswer.Granted }
            local.update { it.copy(askLocalNetwork = false, localNetwork = refused) }
            viewModelScope.launch { query() }
        }

        /** For a permission Android no longer asks for: the app info page is where it can be allowed. */
        fun openSettings() = actions.openAppSettings()

        private suspend fun query() {
            when (val result = info.info()) {
                is ApiResult.Ok ->
                    local.update {
                        it.copy(
                            serverState = "Connected to Nytka server ${result.value.serverVersion}",
                            apiVersion = result.value.apiVersion,
                            apiMismatch = result.value.apiVersion != NytkaApi.API_VERSION,
                            // The note is for a server out of reach: this one answered.
                            localNetwork = null,
                        )
                    }
                is ApiResult.Failure ->
                    local.update {
                        it.copy(
                            serverState = result.message,
                            apiVersion = null,
                            apiMismatch = false,
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
