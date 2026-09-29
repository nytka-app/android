package io.github.nytka_app.capture

import android.content.Intent
import android.os.PowerManager
import io.github.nytka_app.core.diagnostics.EventLog

/** What the phone says about its power right now; a fake in tests. */
interface PowerState {
    val screenOn: Boolean
    val dozing: Boolean
    val powerSave: Boolean
}

/**
 * The log lines for what can quiet a background app: the screen turning off, the phone entering doze (device
 * idle) and power-save mode changing. A stretch without audio is then read beside them. A line is a state word or
 * two, nothing else.
 */
class PowerStateLog(
    private val state: PowerState,
    private val log: EventLog,
) {
    /** The state as capture starts, so the first change has something to be read against. */
    fun logStart() {
        val screen = word(state.screenOn)
        val doze = word(state.dozing)
        val powerSave = word(state.powerSave)
        log.i(TAG, "at start: screen $screen, doze $doze, power save $powerSave")
    }

    /** Logs what the system broadcast [action] says; any other action is ignored. */
    fun onBroadcast(action: String?) {
        val line =
            when (action) {
                Intent.ACTION_SCREEN_ON -> "screen on"
                Intent.ACTION_SCREEN_OFF -> "screen off"
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> "doze ${word(state.dozing)}"
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> "power save ${word(state.powerSave)}"
                else -> return
            }
        log.i(TAG, line)
    }

    companion object {
        /** The broadcasts [onBroadcast] understands: a receiver registers for exactly these. */
        val ACTIONS =
            listOf(
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
            )
        private const val TAG = "PowerStateLog"

        private fun word(on: Boolean) = if (on) "on" else "off"
    }
}
