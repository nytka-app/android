package io.github.nytka_app.capture

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.FakeEventLog
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PowerBroadcastsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(PowerManager::class.java)
    private val log = FakeEventLog()

    private fun send(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the state is what the PowerManager says`() {
        val state = AndroidPowerState(manager)
        assertEquals(listOf(true, false, false), listOf(state.screenOn, state.dozing, state.powerSave))

        shadowOf(manager).turnScreenOn(false)
        shadowOf(manager).setIsDeviceIdleMode(true)
        shadowOf(manager).setIsPowerSaveMode(true)

        assertEquals(listOf(false, true, true), listOf(state.screenOn, state.dozing, state.powerSave))
    }

    @Test
    fun `logs the state at start and each of the four broadcasts until stopped`() {
        // Set before anyone listens: Robolectric announces an idle-mode change by itself.
        shadowOf(manager).setIsDeviceIdleMode(true)
        shadowOf(manager).setIsPowerSaveMode(true)
        val watcher = PowerBroadcasts(app, log)
        watcher.start()

        send(Intent.ACTION_SCREEN_OFF)
        send(Intent.ACTION_SCREEN_ON)
        send(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        send(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        watcher.stop()
        send(Intent.ACTION_SCREEN_OFF)
        send(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)

        assertEquals(
            listOf(
                "at start: screen on, doze on, power save on",
                "screen off",
                "screen on",
                "doze on",
                "power save on",
            ),
            log.messages,
        )
    }

    @Test
    fun `starting twice registers once`() {
        val watcher = PowerBroadcasts(app, log)
        watcher.start()
        watcher.start()

        send(Intent.ACTION_SCREEN_OFF)

        assertEquals(listOf("at start: screen on, doze off, power save off", "screen off"), log.messages)
    }

    @Test
    fun `a start that comes after the stop registers nothing`() {
        val watcher = PowerBroadcasts(app, log)
        watcher.stop()
        watcher.start()

        send(Intent.ACTION_SCREEN_OFF)

        assertEquals(emptyList<String>(), log.messages)
    }
}
