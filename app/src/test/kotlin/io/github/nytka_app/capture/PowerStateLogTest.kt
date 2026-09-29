package io.github.nytka_app.capture

import android.content.Intent
import android.os.PowerManager
import io.github.nytka_app.FakeEventLog
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerStateLogTest {
    private class FakePowerState(
        override var screenOn: Boolean = true,
        override var dozing: Boolean = false,
        override var powerSave: Boolean = false,
    ) : PowerState

    private val state = FakePowerState()
    private val log = FakeEventLog()
    private val powerLog = PowerStateLog(state, log)

    @Test
    fun `the screen turning on or off is logged from the broadcast itself`() {
        powerLog.onBroadcast(Intent.ACTION_SCREEN_OFF)
        powerLog.onBroadcast(Intent.ACTION_SCREEN_ON)

        assertEquals(listOf("screen off", "screen on"), log.messages)
    }

    @Test
    fun `doze and power save are read from the state when their broadcast arrives`() {
        state.dozing = true
        powerLog.onBroadcast(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        state.dozing = false
        powerLog.onBroadcast(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        state.powerSave = true
        powerLog.onBroadcast(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        state.powerSave = false
        powerLog.onBroadcast(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)

        assertEquals(listOf("doze on", "doze off", "power save on", "power save off"), log.messages)
    }

    @Test
    fun `the start line holds the whole state`() {
        state.screenOn = false
        state.powerSave = true
        powerLog.logStart()

        assertEquals(listOf("at start: screen off, doze off, power save on"), log.messages)
    }

    @Test
    fun `any other broadcast is ignored`() {
        powerLog.onBroadcast(Intent.ACTION_BATTERY_CHANGED)
        powerLog.onBroadcast(null)

        assertEquals(emptyList<String>(), log.messages)
    }

    @Test
    fun `every action a receiver registers for is understood`() {
        PowerStateLog.ACTIONS.forEach(powerLog::onBroadcast)

        assertEquals(PowerStateLog.ACTIONS.size, log.lines.size)
    }
}
