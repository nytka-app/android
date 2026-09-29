package io.github.nytka_app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import io.github.nytka_app.core.diagnostics.EventLog

/** [PowerState] as the system's PowerManager reports it. */
class AndroidPowerState(
    private val power: PowerManager,
) : PowerState {
    override val screenOn: Boolean get() = power.isInteractive
    override val dozing: Boolean get() = power.isDeviceIdleMode
    override val powerSave: Boolean get() = power.isPowerSaveMode
}

/**
 * Logs the phone's screen, doze and power-save changes between [start] and [stop]. The capture service starts it
 * with its log run and stops it in onDestroy, so nothing listens while capture does not run.
 */
class PowerBroadcasts(
    private val context: Context,
    log: EventLog,
) {
    private val target = PowerStateLog(AndroidPowerState(context.getSystemService(PowerManager::class.java)), log)
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = target.onBroadcast(intent.action)
        }
    private var running = false
    private var stopped = false

    /** Registers and logs the state as it is. Once [stop] has run it does nothing, so a late start cannot leak. */
    @Synchronized
    fun start() {
        if (running || stopped) return
        running = true
        val filter = IntentFilter().apply { PowerStateLog.ACTIONS.forEach { addAction(it) } }
        // All four are protected system broadcasts, sent by the system UID, which a not-exported receiver accepts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        target.logStart()
    }

    @Synchronized
    fun stop() {
        stopped = true
        if (!running) return
        running = false
        context.unregisterReceiver(receiver)
    }
}
