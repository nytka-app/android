package io.github.nytka_app.alerts

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.core.queue.FrameQueue

object AlertNotifications {
    fun text(
        resources: Resources,
        alert: Alert,
        inputs: AlertInputs,
    ): String =
        when (alert) {
            Alert.PendantAway ->
                inputs.disconnectedAfterMinutes.let {
                    resources.getQuantityString(R.plurals.alert_pendant_away_format, it, it)
                }

            Alert.ServerUnreachable ->
                inputs.unreachableAfterMinutes.let {
                    resources.getQuantityString(R.plurals.alert_server_unreachable_format, it, it)
                }

            Alert.BatteryLow -> resources.getString(R.string.alert_battery_low_format, inputs.battery ?: 0)
            Alert.QueueFilling ->
                resources.getString(R.string.alert_queue_filling_format, (FrameQueue.ALERT_FRACTION * 100).toInt())
        }

    fun post(
        context: Context,
        alert: Alert,
        inputs: AlertInputs,
    ) {
        if (!Notifier.canPost(context)) return
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, Notifier.ALERTS_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(Notifier.ACCENT)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text(context.resources, alert, inputs))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text(context.resources, alert, inputs)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        context.getSystemService(NotificationManager::class.java).notify(id(alert), notification)
    }

    fun cancel(
        context: Context,
        alert: Alert,
    ) {
        context.getSystemService(NotificationManager::class.java).cancel(id(alert))
    }

    private fun id(alert: Alert) = ALERT_ID_BASE + alert.ordinal

    private const val ALERT_ID_BASE = 100
}
