package io.github.nytka_app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
            preferences[DEVELOPER_MODE] = next.developerMode
            preferences[FAKE_PENDANT] = next.fakePendant
            preferences[ALERT_DISCONNECTED] = next.alertDisconnectedMinutes
            preferences[ALERT_UNREACHABLE] = next.alertUnreachableMinutes
            preferences[ALERT_BATTERY] = next.alertBatteryPercent
        }
    }

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
            developerMode = preferences[DEVELOPER_MODE] ?: false,
            fakePendant = preferences[FAKE_PENDANT] ?: false,
            alertDisconnectedMinutes = preferences[ALERT_DISCONNECTED] ?: Settings().alertDisconnectedMinutes,
            alertUnreachableMinutes = preferences[ALERT_UNREACHABLE] ?: Settings().alertUnreachableMinutes,
            alertBatteryPercent = preferences[ALERT_BATTERY] ?: Settings().alertBatteryPercent,
        )

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val TOKEN = stringPreferencesKey("token_encrypted")
        private val PRIVATE_NETWORK = booleanPreferencesKey("private_network")
        private val MUTED = booleanPreferencesKey("muted")
        private val PENDANT_ADDRESS = stringPreferencesKey("pendant_address")
        private val PENDANT_NAME = stringPreferencesKey("pendant_name")
        private val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val FAKE_PENDANT = booleanPreferencesKey("fake_pendant")
        private val ALERT_DISCONNECTED = intPreferencesKey("alert_disconnected_minutes")
        private val ALERT_UNREACHABLE = intPreferencesKey("alert_unreachable_minutes")
        private val ALERT_BATTERY = intPreferencesKey("alert_battery_percent")

        fun create(
            context: Context,
            cipher: TokenCipher = KeystoreTokenCipher(),
        ) = SettingsStore(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") }),
            cipher,
        )
    }
}
