package io.github.nytka_app.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Reversible and obviously not the plain text, which is all these tests need. */
    private class FakeCipher : TokenCipher {
        var readable = true

        override fun encrypt(plain: String) = "enc:" + plain.reversed()

        override fun decrypt(stored: String) = if (readable) stored.removePrefix("enc:").reversed() else null
    }

    private val cipher = FakeCipher()
    private val file by lazy { folder.newFile("settings.preferences_pb").also { it.delete() } }

    private fun TestScope.store() =
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
            cipher,
        )

    @Test
    fun `defaults before anything is saved`() =
        runTest {
            assertEquals(Settings(), store().current())
        }

    @Test
    fun `round trips every field`() =
        runTest {
            val wanted =
                Settings(
                    serverUrl = "https://nytka.example",
                    token = "t".repeat(40),
                    privateNetwork = true,
                    muted = true,
                    pendantAddress = "AA:BB:CC:DD:EE:FF",
                    pendantName = "Omi",
                    consentGiven = true,
                    onboarded = true,
                    firstRunStep = FirstRunStep.Consent,
                    developerMode = true,
                    fakePendant = true,
                    alertDisconnectedMinutes = 1,
                    alertUnreachableMinutes = 2,
                    alertBatteryPercent = 30,
                    diagnosticsUpload = true,
                    muteSchedule =
                        MuteSchedule(
                            listOf(
                                MuteWindow(
                                    setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
                                    LocalTime.of(22, 0),
                                    LocalTime.of(7, 30),
                                ),
                            ),
                        ),
                )
            val store = store()

            store.update { wanted }

            assertEquals(wanted, store.current())
        }

    @Test
    fun `stores the token only encrypted`() =
        runTest {
            store().update { it.copy(token = "secret-token-value-0123456789abcdef") }

            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("secret-token-value"))
        }

    @Test
    fun `an unreadable token reads as empty`() =
        runTest {
            val store = store()
            store.update { it.copy(token = "secret-token-value-0123456789abcdef") }
            cipher.readable = false

            assertEquals("", store.current().token)
        }

    @Test
    fun `toString never shows the token`() {
        assertFalse(Settings(token = "secret-token-value").toString().contains("secret"))
    }

    @Test
    fun `diagnostics upload is off until switched on`() =
        runTest {
            val store = store()

            assertFalse(store.current().diagnosticsUpload)
            store.update { it.copy(diagnosticsUpload = true) }
            assertEquals(true, store.current().diagnosticsUpload)
            assertEquals(true, store.current().toString().contains("diagnosticsUpload=true"))
        }

    @Test
    fun `first run starts at the server step and keeps the step it reached`() =
        runTest {
            val store = store()

            assertEquals(FirstRunStep.Server, store.current().firstRunStep)
            store.update { it.copy(firstRunStep = FirstRunStep.Permissions) }
            assertEquals(FirstRunStep.Permissions, store.current().firstRunStep)
        }

    @Test
    fun `the step reached is read back after the app restarts`() =
        runTest {
            val firstProcess = Job()
            val before =
                SettingsStore(
                    PreferenceDataStoreFactory.create(scope = CoroutineScope(firstProcess), produceFile = { file }),
                    cipher,
                )
            before.update { it.copy(firstRunStep = FirstRunStep.Pairing) }
            firstProcess.cancelAndJoin()

            assertEquals(FirstRunStep.Pairing, store().current().firstRunStep)
        }

    @Test
    fun `a stored step this version does not know starts first run over`() =
        runTest {
            val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
            dataStore.edit { it[stringPreferencesKey("first_run_step")] = "Retired" }

            assertEquals(FirstRunStep.Server, SettingsStore(dataStore, cipher).current().firstRunStep)
        }
}
