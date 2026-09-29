package io.github.nytka_app.capture

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.pendant.PendantConnection

object CaptureNotification {
    const val ID = 1
    const val CHANNEL = Notifier.RECORDING_CHANNEL

    /** "Recording · 82% · 0 queued", as the spec words it. */
    fun text(
        status: CaptureStatus,
        usage: QueueUsage,
    ): String {
        val state =
            when {
                status.connection is PendantConnection.Refused -> "Pendant not supported"
                status.muted -> "Muted"
                status.connection is PendantConnection.Connected -> "Recording"
                else -> "Waiting for the pendant"
            }
        val battery = status.battery?.let { " · $it%" } ?: ""
        return "$state$battery · ${usage.chunks} queued"
    }

    fun build(
        context: Context,
        status: CaptureStatus,
        usage: QueueUsage,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val toggle =
            PendingIntent.getService(
                context,
                1,
                Intent(context, CaptureService::class.java)
                    .setAction(if (status.muted) CaptureService.ACTION_UNMUTE else CaptureService.ACTION_MUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nytka")
            .setContentText(text(status, usage))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (status.muted) "Unmute" else "Mute", toggle)
            .build()
    }
}
