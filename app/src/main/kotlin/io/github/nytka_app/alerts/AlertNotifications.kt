package io.github.nytka_app.alerts

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.core.queue.FrameQueue

object AlertNotifications {
    fun text(
        alert: Alert,
        inputs: AlertInputs,
    ): String =
        when (alert) {
            Alert.PendantAway -> "The pendant has been disconnected for ${inputs.disconnectedAfterMinutes} minutes."
            Alert.ServerUnreachable ->
                "The server has been unreachable for ${inputs.unreachableAfterMinutes} minutes. " +
                    "Audio waits in the queue."
            Alert.BatteryLow -> "The pendant battery is at ${inputs.battery}%."
            Alert.QueueFilling ->
                "The upload queue is ${(FrameQueue.ALERT_FRACTION * 100).toInt()}% full; " +
                    "the oldest audio goes first when it fills."
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
                .setContentTitle("Nytka")
                .setContentText(text(alert, inputs))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text(alert, inputs)))
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
