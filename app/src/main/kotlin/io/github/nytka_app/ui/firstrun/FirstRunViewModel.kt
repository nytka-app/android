package io.github.nytka_app.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerUrl
import io.github.nytka_app.core.api.UrlCheck
import io.github.nytka_app.core.settings.FirstRunStep
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.after
import io.github.nytka_app.ui.mustAskForLocalNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** docs/specs/v0.1.md, first run, step 3. Keep it word for word. */
const val CONSENT_TEXT =
    "Recording people without their consent is illegal in some places. " +
        "You are responsible for following the law where you use Nytka."

data class FirstRunUiState(
    /** False until the saved step and server details are read; the screen shows nothing before, not step 1. */
    val loaded: Boolean = false,
    val step: FirstRunStep = FirstRunStep.Server,
    val url: String = "",
    val token: String = "",
    val privateNetwork: Boolean = false,
    val testing: Boolean = false,
    val serverError: String? = null,
    val serverVersion: String? = null,
    /** True while the screen should show Android's prompt for the local network. */
    val askLocalNetwork: Boolean = false,
    /**
     * True when the last connection test failed on the network. On Android 17 that can be the local network
     * permission, whatever the address looks like, so the screen offers to allow it.
     */
    val serverUnreachable: Boolean = false,
    /** How the user last answered the nearby-devices prompt, unless they allowed it. */
    val permissions: PermissionAnswer? = null,
    val consentChecked: Boolean = false,
    val pairError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class FirstRunViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val info: InfoClient,
        private val actions: DeviceActions,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(FirstRunUiState())
        val state: StateFlow<FirstRunUiState> = mutableState.asStateFlow()

        init {
            // Android can end the app while the user is away, in the system settings to allow a permission, say.
            // Pick up at the step reached, with the server details a connection test saved.
            viewModelScope.launch {
                val saved = settings.current()
                mutableState.update {
                    it.copy(
                        loaded = true,
                        step = saved.firstRunStep,
                        url = saved.serverUrl,
                        token = saved.token,
                        privateNetwork = saved.privateNetwork,
                    )
                }
            }
        }

        fun edit(
            url: String,
            token: String,
            privateNetwork: Boolean,
        ) {
            mutableState.update {
                it.copy(
                    url = url,
                    token = token,
                    privateNetwork = privateNetwork,
                    serverError = null,
                )
            }
        }

        fun testConnection() {
            val current = mutableState.value
            val base =
                when (val check = ServerUrl.check(current.url, current.privateNetwork)) {
                    is UrlCheck.Invalid -> return mutableState.update { it.copy(serverError = check.reason) }
                    is UrlCheck.Ok -> check.base
                }
            if (current.token.isBlank()) {
                return mutableState.update {
                    it.copy(
                        serverError = "Enter the server's token.",
                    )
                }
            }

            // Android 17 blocks a server on the local network until Nytka may use it: ask before the test when the
            // address looks like one. Any other server that fails to answer brings the hint instead.
            if (mustAskForLocalNetwork(base, current.privateNetwork, actions)) {
                return mutableState.update { it.copy(askLocalNetwork = true, serverError = null) }
            }
            connect(base.toString(), current)
        }

        /**
         * The test goes on whatever the answer: over a VPN the server is reachable without the permission, and if it
         * is not, the hint under the address says why.
         */
        fun localNetworkAnswered() {
            mutableState.update { it.copy(askLocalNetwork = false) }
            val current = mutableState.value
            (ServerUrl.check(current.url, current.privateNetwork) as? UrlCheck.Ok)
                ?.let { connect(it.base.toString(), current) }
        }

        private fun connect(
            base: String,
            current: FirstRunUiState,
        ) {
            mutableState.update { it.copy(testing = true, serverError = null, serverUnreachable = false) }
            viewModelScope.launch {
                // Saved first: the API client reads the settings on every request.
                settings.update {
                    it.copy(
                        serverUrl = base,
                        token = current.token.trim(),
                        privateNetwork = current.privateNetwork,
                    )
                }
                when (val result = info.info()) {
                    is ApiResult.Ok -> {
                        settings.update { it.copy(firstRunStep = FirstRunStep.Permissions) }
                        mutableState.update {
                            it.copy(
                                testing = false,
                                serverVersion = result.value.serverVersion,
                                step = FirstRunStep.Permissions,
                            )
                        }
                    }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                testing = false,
                                serverError = result.message,
                                serverUnreachable = result.kind == FailureKind.Network,
                            )
                        }
                }
            }
        }

        /** Nearby devices is required to go on; a refused notification permission does not hold anyone back. */
        fun permissionsAnswered(answer: PermissionAnswer) {
            if (answer == PermissionAnswer.Granted) {
                permissionsDone()
            } else {
                mutableState.update { it.copy(permissions = answer.after(it.permissions)) }
            }
        }

        fun permissionsDone() {
            viewModelScope.launch {
                settings.update { it.copy(firstRunStep = FirstRunStep.Consent) }
                mutableState.update { it.copy(step = FirstRunStep.Consent) }
            }
        }

        /** For a permission Android no longer asks for: the app info page is where it can be allowed. */
        fun openSettings() = actions.openAppSettings()

        fun setConsent(checked: Boolean) {
            mutableState.update { it.copy(consentChecked = checked) }
        }

        fun acceptConsent() {
            if (!mutableState.value.consentChecked) return
            viewModelScope.launch {
                settings.update { it.copy(consentGiven = true, firstRunStep = FirstRunStep.Pairing) }
                mutableState.update { it.copy(step = FirstRunStep.Pairing) }
            }
        }

        fun paired(pendant: PairedPendant) {
            viewModelScope.launch {
                settings.update {
                    it.copy(
                        pendantAddress = pendant.address,
                        pendantName = pendant.name,
                        fakePendant = false,
                        onboarded = true,
                    )
                }
                actions.startCapture()
                mutableState.update { it.copy(done = true) }
            }
        }

        fun pairingFailed(reason: String) {
            mutableState.update { it.copy(pairError = reason) }
        }

        fun skipPairing() {
            viewModelScope.launch {
                settings.update { it.copy(onboarded = true) }
                mutableState.update { it.copy(done = true) }
            }
        }
    }
