package io.github.nytka_app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.BuildConfig
import io.github.nytka_app.briefs.BriefScheduler
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.capture.PendantSettingsControls
import io.github.nytka_app.capture.PendantSettingsState
import io.github.nytka_app.capture.SyncControls
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.core.api.ServerUrl
import io.github.nytka_app.core.api.UrlCheck
import io.github.nytka_app.core.settings.MuteSchedule
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.firmware.FirmwareNotice
import io.github.nytka_app.firmware.FirmwareNotices
import io.github.nytka_app.pendant.PendantInfo
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.after
import io.github.nytka_app.ui.firstrun.READ_TOKEN_REFUSED
import io.github.nytka_app.ui.mustAskForLocalNetwork
import kotlinx.coroutines.Job
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
    /** The Pendant settings card; null hides it (not connected, or the pendant reports neither setting). */
    val pendantSettings: PendantSettingsState? = null,
    /** Packets the first sync found, while it waits for "import or discard"; null when nothing is asked. */
    val backlogPackets: Long? = null,
    /** Whether /info lists `offline-sync`; null until it answers and after a failed check. Only true shows the card. */
    val serverSync: Boolean? = null,
    /** Whether /info lists `voice` (the server has the speaker model); only true shows "Your voice". */
    val serverVoice: Boolean? = null,
    val muteSchedule: MuteSchedule = MuteSchedule(),
    /** The line under the mute editor: whether the server has the schedule; null until the first answer. */
    val muteServer: MuteServerState? = null,
    /** The phone's time zone id when the server's zone is UTC or unset and the phone's is not; else null. */
    val timeZoneHint: String? = null,
    val timeZoneError: String? = null,
    /** Whether Nytka looks for a newer pendant firmware (a setting, on by default). */
    val firmwareCheck: Boolean = true,
    /** A newer firmware than the connected pendant runs; null when none is known. */
    val firmwareNotice: FirmwareNotice? = null,
    val consentChime: Boolean = false,
    val consentChimeMinutes: Int = 15,
    /** Whether the phone tells the server when it plays sound through its speaker or is in a call (a setting). */
    val phoneContext: Boolean = true,
    /** Whether /info lists `context-ranges`; null until it answers or after a failed check. */
    val serverContext: Boolean? = null,
    /** Whether meeting briefs post notifications (a setting, off by default). */
    val briefNotifications: Boolean = false,
    /** How the user answered the notification prompt when switching briefs on; null until asked. */
    val briefPermission: PermissionAnswer? = null,
    /** The line under the brief switch; null when the server has a calendar feed or has not answered. */
    val calendarLine: CalendarLine? = null,
)

enum class CalendarLine {
    /** `GET /settings` shows `calendar.icsUrl` not set. */
    NoFeed,

    /** The key is missing: the server is older than the calendar feed. */
    NeedsUpdate,
}

sealed interface MuteServerState {
    data object Applied : MuteServerState

    data class Failed(
        val reason: String,
    ) : MuteServerState
}

/** The phone's IANA time zone id, e.g. `Europe/Kyiv`. */
fun interface PhoneZone {
    fun id(): String
}

@Suppress("TooManyFunctions") // one screen, one function per control
@HiltViewModel
class DeviceViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val info: InfoClient,
        private val actions: DeviceActions,
        private val sync: SyncControls,
        private val serverSettings: ServerSettingsClient,
        private val phoneZone: PhoneZone,
        private val pendantSettings: PendantSettingsControls,
        private val firmware: FirmwareNotices,
        private val briefs: BriefScheduler,
    ) : ViewModel() {
        private val local = MutableStateFlow(DeviceUiState())

        val state: StateFlow<DeviceUiState> =
            combine(
                settings.settings,
                local,
                sync.status,
                sync.connected,
                pendantSettings.state,
            ) { current, screen, storage, connected, pendant ->
                screen.copy(
                    pendantSettings = pendant.takeIf { connected && (it.led != null || it.gain != null) },
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
                    firmwareCheck = current.firmwareCheck,
                    consentChime = current.consentChime,
                    consentChimeMinutes = current.consentChimeMinutes,
                    phoneContext = current.phoneContext,
                    briefNotifications = current.briefNotifications,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceUiState())

        init {
            checkServer()
        }

        fun checkServer() {
            local.update { it.copy(serverState = "Checking…") }
            syncMuteWithServer()
            loadCalendarLine()
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
            syncMuteWithServer()
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
                            serverVoice = result.value.has(ServerInfo.FEATURE_VOICE),
                            serverContext = result.value.has(ServerInfo.FEATURE_CONTEXT_RANGES),
                        )
                    }

                is ApiResult.Failure ->
                    local.update {
                        it.copy(
                            serverState = result.message,
                            apiVersion = null,
                            apiMismatch = false,
                            serverSync = null,
                            serverVoice = null,
                            serverContext = null,
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

        private var muteJob: Job? = null

        /**
         * The capture service watches the setting, so a change needs no restart. The pendant syncs audio recorded
         * while away, so the server gets the windows too.
         */
        fun setMuteSchedule(schedule: MuteSchedule) {
            muteJob?.cancel()
            muteJob =
                viewModelScope.launch {
                    settings.update { it.copy(muteSchedule = schedule) }
                    pushMute(schedule)
                }
        }

        /** On the start and after a server change: the app's schedule wins if the server holds another one. */
        private fun syncMuteWithServer() {
            muteJob?.cancel()
            muteJob =
                viewModelScope.launch {
                    val schedule = settings.current().muteSchedule
                    when (val catalog = serverSettings.settings()) {
                        is ApiResult.Failure -> reportMute(catalog)
                        is ApiResult.Ok -> {
                            val stored = catalog.value.firstOrNull { it.key == MUTE_KEY }
                            when {
                                stored == null -> reportMute(null)
                                MuteSchedule.sameOnServer(stored.value, schedule.toServerJson()) -> {
                                    local.update { it.copy(muteServer = MuteServerState.Applied) }
                                    showZoneHint(catalog.value)
                                }

                                else -> pushMute(schedule)
                            }
                        }
                    }
                }
        }

        private suspend fun pushMute(schedule: MuteSchedule) {
            when (val result = serverSettings.update(mapOf(MUTE_KEY to schedule.toServerJson()))) {
                is ApiResult.Failure -> reportMute(result)
                is ApiResult.Ok -> {
                    local.update { it.copy(muteServer = MuteServerState.Applied) }
                    showZoneHint(result.value)
                }
            }
        }

        private fun reportMute(failure: ApiResult.Failure?) {
            local.update {
                it.copy(
                    muteServer = MuteServerState.Failed(muteFailureReason(failure)),
                    timeZoneHint = null,
                )
            }
        }

        private fun showZoneHint(catalog: List<ServerSetting>) {
            val server = catalog.firstOrNull { it.key == ZONE_KEY } ?: return
            val zone = (server.value ?: server.default).orEmpty()
            val phone = phoneZone.id()
            val hint = phone.takeIf { isUtc(zone) && !isUtc(phone) }
            local.update { it.copy(timeZoneHint = hint, timeZoneError = null) }
        }

        /** One tap: the server evaluates the windows in the phone's zone from now on. */
        fun setServerTimeZone() {
            val phone = phoneZone.id()
            viewModelScope.launch {
                when (val result = serverSettings.update(mapOf(ZONE_KEY to phone))) {
                    is ApiResult.Ok -> local.update { it.copy(timeZoneHint = null, timeZoneError = null) }
                    is ApiResult.Failure ->
                        local.update {
                            it.copy(
                                timeZoneError = "Could not set the server's time zone: ${zoneFailure(result)}",
                            )
                        }
                }
            }
        }

        /** A finished slider gesture: the controller writes it, at most once every 2 seconds. */
        fun commitLed(percent: Int) = pendantSettings.commitLed(percent)

        fun commitGain(level: Int) = pendantSettings.commitGain(level)

        private var firmwareJob: Job? = null

        /** The connected pendant's info, or null when none is connected; may ask GitHub, at most once a day. */
        fun pendantSeen(info: PendantInfo?) {
            firmwareJob?.cancel()
            firmwareJob =
                viewModelScope.launch {
                    val notice = info?.let { firmware.notice(it) }
                    local.update { it.copy(firmwareNotice = notice) }
                }
        }

        fun setFirmwareCheck(on: Boolean) {
            viewModelScope.launch { settings.update { it.copy(firmwareCheck = on) } }
        }

        fun setConsentChime(on: Boolean) {
            viewModelScope.launch { settings.update { it.copy(consentChime = on) } }
        }

        fun setPhoneContext(on: Boolean) {
            viewModelScope.launch { settings.update { it.copy(phoneContext = on) } }
        }

        fun setConsentChimeMinutes(minutes: Int) {
            viewModelScope.launch {
                settings.update {
                    it.copy(
                        consentChimeMinutes = minutes.coerceIn(CHIME_MINUTES_MIN, CHIME_MINUTES_MAX),
                    )
                }
            }
        }

        private fun loadCalendarLine() {
            viewModelScope.launch {
                val line =
                    when (val result = serverSettings.settings()) {
                        is ApiResult.Ok ->
                            result.value.firstOrNull { it.key == CALENDAR_KEY }.let {
                                when {
                                    it == null -> CalendarLine.NeedsUpdate
                                    it.isSet -> null
                                    else -> CalendarLine.NoFeed
                                }
                            }

                        is ApiResult.Failure ->
                            CalendarLine.NeedsUpdate.takeIf {
                                result.kind == FailureKind.NotFound || result.kind == FailureKind.Unsupported
                            }
                    }
                local.update { it.copy(calendarLine = line) }
            }
        }

        /** Off cancels the job. On saves the setting and schedules it, after the screen has asked for permission. */
        fun setBriefNotifications(on: Boolean) {
            viewModelScope.launch {
                settings.update { it.copy(briefNotifications = on) }
                local.update { it.copy(briefPermission = null) }
                if (on) briefs.schedule() else briefs.cancel()
            }
        }

        /** The answer to Android's notification prompt, asked when the switch went on without the permission. */
        fun briefPermissionAnswered(answer: PermissionAnswer) {
            if (answer == PermissionAnswer.Granted) {
                setBriefNotifications(true)
            } else {
                local.update { it.copy(briefPermission = answer.after(it.briefPermission)) }
            }
        }

        /** For a permission Android no longer asks for: the app info page is where it can be allowed. */
        fun openAppSettings() = actions.openAppSettings()

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
            const val MUTE_KEY = "mute.windows"
            const val ZONE_KEY = "user.timeZone"
            const val CALENDAR_KEY = "calendar.icsUrl"

            private fun isUtc(zone: String) =
                zone.isBlank() || zone.uppercase() in setOf("UTC", "ETC/UTC", "Z", "GMT", "ETC/GMT")

            /** Plain words for the line under the editor; null is a server without the setting. */
            fun muteFailureReason(failure: ApiResult.Failure?): String =
                when (failure?.kind) {
                    null, FailureKind.NotFound, FailureKind.Invalid, FailureKind.Unsupported ->
                        "this server does not know mute windows yet, so audio recorded while the pendant is away " +
                            "is not filtered there."

                    else -> zoneFailure(failure)
                }

            private fun zoneFailure(failure: ApiResult.Failure): String =
                when (failure.kind) {
                    FailureKind.NotFound, FailureKind.Invalid, FailureKind.Unsupported ->
                        "this server does not support it yet."

                    FailureKind.Network -> "the server could not be reached."
                    FailureKind.NotConfigured -> "no server is set up."
                    FailureKind.Unauthorized -> "the token was refused."
                    FailureKind.Forbidden -> "this token cannot change server settings."
                    FailureKind.Conflict -> "the setting is locked on the server."
                    FailureKind.Server, FailureKind.Unavailable, FailureKind.Timeout, FailureKind.BadGateway ->
                        "the server reported an error."
                }
        }
    }
