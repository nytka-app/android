package io.github.nytka_app.alerts

import io.github.nytka_app.capture.CaptureStatus
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.upload.UploadState

enum class Alert { PendantAway, ServerUnreachable, BatteryLow, QueueFilling }

data class AlertInputs(
    val running: Boolean,
    val disconnectedSinceMs: Long?,
    val unreachableSinceMs: Long?,
    val battery: Int?,
    val queueFraction: Double,
    val disconnectedAfterMinutes: Int,
    val unreachableAfterMinutes: Int,
    val batteryPercent: Int,
) {
    companion object {
        fun of(
            status: CaptureStatus,
            upload: UploadState,
            usage: QueueUsage,
            settings: Settings,
        ) = AlertInputs(
            running = status.running,
            disconnectedSinceMs = status.disconnectedSinceMs,
            unreachableSinceMs = upload.unreachableSinceMs,
            battery = status.battery,
            queueFraction = usage.fraction,
            disconnectedAfterMinutes = settings.alertDisconnectedMinutes,
            unreachableAfterMinutes = settings.alertUnreachableMinutes,
            batteryPercent = settings.alertBatteryPercent,
        )
    }
}

data class AlertChange(
    val started: List<Alert>,
    val cleared: List<Alert>,
)

/** Remembers which alerts are up, so each fires once per episode. */
class AlertMonitor {
    private val active = mutableSetOf<Alert>()

    fun evaluate(
        inputs: AlertInputs,
        nowMs: Long,
    ): AlertChange {
        val now = Alert.entries.filter { condition(it, inputs, nowMs, it in active) }.toSet()
        val started = Alert.entries.filter { it in now && it !in active }
        val cleared = Alert.entries.filter { it !in now && it in active }
        active.clear()
        active += now
        return AlertChange(started, cleared)
    }

    private fun condition(
        alert: Alert,
        inputs: AlertInputs,
        nowMs: Long,
        up: Boolean,
    ): Boolean =
        when (alert) {
            Alert.PendantAway ->
                inputs.running &&
                    inputs.disconnectedSinceMs.olderThan(inputs.disconnectedAfterMinutes, nowMs)
            Alert.ServerUnreachable -> inputs.unreachableSinceMs.olderThan(inputs.unreachableAfterMinutes, nowMs)
            Alert.BatteryLow ->
                inputs.battery != null &&
                    inputs.battery <= inputs.batteryPercent + if (up) BATTERY_CLEAR_MARGIN else 0
            Alert.QueueFilling ->
                inputs.queueFraction >=
                    FrameQueue.ALERT_FRACTION - if (up) QUEUE_CLEAR_MARGIN else 0.0
        }

    private fun Long?.olderThan(
        minutes: Int,
        nowMs: Long,
    ) = this != null && nowMs - this >= minutes * MINUTE_MS

    private companion object {
        const val MINUTE_MS = 60_000L
        const val BATTERY_CLEAR_MARGIN = 5
        const val QUEUE_CLEAR_MARGIN = 0.05
    }
}
