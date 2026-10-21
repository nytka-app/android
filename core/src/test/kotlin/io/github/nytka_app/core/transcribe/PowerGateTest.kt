package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.settings.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerGateTest {
    private val on = Settings(phoneTranscription = true)

    @Test
    fun `off never runs, even on a charger`() {
        assertFalse(PowerGate.allows(Settings(), level = 100, charging = true))
    }

    @Test
    fun `by default it runs only on a charger`() {
        assertTrue(PowerGate.allows(on, level = 10, charging = true))
        assertFalse(PowerGate.allows(on, level = 100, charging = false))
    }

    @Test
    fun `on battery it runs at or above the threshold`() {
        val battery = on.copy(phoneTranscriptionOnBattery = true, phoneTranscriptionBatteryPercent = 60)

        assertTrue(PowerGate.allows(battery, level = 60, charging = false))
        assertFalse(PowerGate.allows(battery, level = 59, charging = false))
        assertFalse(PowerGate.allows(battery, level = null, charging = null))
    }
}
