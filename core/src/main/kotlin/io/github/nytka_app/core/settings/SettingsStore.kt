package io.github.nytka_app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : SettingsSource {
    // Decrypting the token calls the Keystore: keep it off the collector's (often main) thread.
    override val settings: Flow<Settings> = dataStore.data.map(::read).flowOn(Dispatchers.IO)

    override suspend fun current(): Settings = settings.first()

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { preferences ->
            val current = read(preferences)
            val next = transform(current)
            preferences[SERVER_URL] = next.serverUrl
            // Touch the stored token only when it changes: a token the Keystore cannot read right now
            // reads as "", and an unrelated update (mute, say) must not erase it.
            if (next.token != current.token) {
                if (next.token.isEmpty()) preferences.remove(TOKEN) else preferences[TOKEN] = cipher.encrypt(next.token)
            }
            preferences[PRIVATE_NETWORK] = next.privateNetwork
            preferences[MUTED] = next.muted
            next.pendantAddress?.let { preferences[PENDANT_ADDRESS] = it } ?: preferences.remove(PENDANT_ADDRESS)
            next.pendantName?.let { preferences[PENDANT_NAME] = it } ?: preferences.remove(PENDANT_NAME)
            preferences[CONSENT_GIVEN] = next.consentGiven
            preferences[ONBOARDED] = next.onboarded
            preferences[FIRST_RUN_STEP] = next.firstRunStep.name
            preferences[DEVELOPER_MODE] = next.developerMode
            preferences[FAKE_PENDANT] = next.fakePendant
            preferences[ALERT_DISCONNECTED] = next.alertDisconnectedMinutes
            preferences[ALERT_UNREACHABLE] = next.alertUnreachableMinutes
            preferences[ALERT_BATTERY] = next.alertBatteryPercent
            preferences[DIAGNOSTICS_UPLOAD] = next.diagnosticsUpload
            preferences[MUTE_SCHEDULE] = next.muteSchedule.encode()
            preferences[FIRMWARE_CHECK] = next.firmwareCheck
            preferences[FIRMWARE_CHECKED_AT] = next.firmwareCheckedAt
            next.firmwareLatest?.let { preferences[FIRMWARE_LATEST] = it } ?: preferences.remove(FIRMWARE_LATEST)
            preferences[HIGH_PRIORITY_CONNECTION] = next.highPriorityConnection
            preferences[BRIEF_NOTIFICATIONS] = next.briefNotifications
            preferences[NOTIFIED_BRIEFS] = next.notifiedBriefs.joinToString(",")
            preferences[CONSENT_CHIME] = next.consentChime
            preferences[CONSENT_CHIME_MINUTES] = next.consentChimeMinutes
            preferences[PHONE_CONTEXT] = next.phoneContext
        }
    }

    @Suppress("CyclomaticComplexMethod") // one default per stored field, nothing branches
    private fun read(preferences: Preferences) =
        Settings(
            serverUrl = preferences[SERVER_URL] ?: "",
            token = preferences[TOKEN]?.let(cipher::decrypt) ?: "",
            privateNetwork = preferences[PRIVATE_NETWORK] ?: false,
            muted = preferences[MUTED] ?: false,
            pendantAddress = preferences[PENDANT_ADDRESS],
            pendantName = preferences[PENDANT_NAME],
            consentGiven = preferences[CONSENT_GIVEN] ?: false,
            onboarded = preferences[ONBOARDED] ?: false,
            firstRunStep = readStep(preferences),
            developerMode = preferences[DEVELOPER_MODE] ?: false,
            fakePendant = preferences[FAKE_PENDANT] ?: false,
            alertDisconnectedMinutes = preferences[ALERT_DISCONNECTED] ?: Settings().alertDisconnectedMinutes,
            alertUnreachableMinutes = preferences[ALERT_UNREACHABLE] ?: Settings().alertUnreachableMinutes,
            alertBatteryPercent = preferences[ALERT_BATTERY] ?: Settings().alertBatteryPercent,
            diagnosticsUpload = preferences[DIAGNOSTICS_UPLOAD] ?: false,
            muteSchedule = MuteSchedule.decode(preferences[MUTE_SCHEDULE]),
            firmwareCheck = preferences[FIRMWARE_CHECK] ?: true,
            firmwareCheckedAt = preferences[FIRMWARE_CHECKED_AT] ?: 0,
            firmwareLatest = preferences[FIRMWARE_LATEST],
            highPriorityConnection = preferences[HIGH_PRIORITY_CONNECTION] ?: false,
            briefNotifications = preferences[BRIEF_NOTIFICATIONS] ?: false,
            notifiedBriefs = preferences[NOTIFIED_BRIEFS].orEmpty().split(',').filter { it.isNotEmpty() },
            consentChime = preferences[CONSENT_CHIME] ?: false,
            consentChimeMinutes = preferences[CONSENT_CHIME_MINUTES] ?: Settings().consentChimeMinutes,
            phoneContext = preferences[PHONE_CONTEXT] ?: true,
        )

    /** Stored by name, so reordering the steps moves no one; a name no longer known starts over. */
    private fun readStep(preferences: Preferences) =
        FirstRunStep.entries.firstOrNull { it.name == preferences[FIRST_RUN_STEP] } ?: FirstRunStep.Server

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val TOKEN = stringPreferencesKey("token_encrypted")
        private val PRIVATE_NETWORK = booleanPreferencesKey("private_network")
        private val MUTED = booleanPreferencesKey("muted")
        private val PENDANT_ADDRESS = stringPreferencesKey("pendant_address")
        private val PENDANT_NAME = stringPreferencesKey("pendant_name")
        private val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val FIRST_RUN_STEP = stringPreferencesKey("first_run_step")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val FAKE_PENDANT = booleanPreferencesKey("fake_pendant")
        private val ALERT_DISCONNECTED = intPreferencesKey("alert_disconnected_minutes")
        private val ALERT_UNREACHABLE = intPreferencesKey("alert_unreachable_minutes")
        private val ALERT_BATTERY = intPreferencesKey("alert_battery_percent")
        private val DIAGNOSTICS_UPLOAD = booleanPreferencesKey("diagnostics_upload")
        private val MUTE_SCHEDULE = stringPreferencesKey("mute_schedule")
        private val FIRMWARE_CHECK = booleanPreferencesKey("firmware_check")
        private val FIRMWARE_CHECKED_AT = longPreferencesKey("firmware_checked_at")
        private val FIRMWARE_LATEST = stringPreferencesKey("firmware_latest")
        private val HIGH_PRIORITY_CONNECTION = booleanPreferencesKey("high_priority_connection")
        private val BRIEF_NOTIFICATIONS = booleanPreferencesKey("brief_notifications")
        private val NOTIFIED_BRIEFS = stringPreferencesKey("notified_briefs")
        private val CONSENT_CHIME = booleanPreferencesKey("consent_chime")
        private val CONSENT_CHIME_MINUTES = intPreferencesKey("consent_chime_minutes")
        private val PHONE_CONTEXT = booleanPreferencesKey("phone_context")

        fun create(
            context: Context,
            cipher: TokenCipher = KeystoreTokenCipher(),
        ) = SettingsStore(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") }),
            cipher,
        )
    }
}
