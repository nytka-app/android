package io.github.nytka_app.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
                    developerMode = true,
                    fakePendant = true,
                    alertDisconnectedMinutes = 1,
                    alertUnreachableMinutes = 2,
                    alertBatteryPercent = 30,
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
}
