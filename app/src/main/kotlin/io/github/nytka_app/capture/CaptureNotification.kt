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
    private const val PERCENT = 100L

    /** "Recording · 82% · 0 queued", as the spec words it. */
    fun text(
        status: CaptureStatus,
        usage: QueueUsage,
        sync: StorageSyncStatus? = null,
    ): String {
        val state =
            when {
                status.connection is PendantConnection.Refused -> "Pendant not supported"
                status.muted -> "Muted"
                status.connection is PendantConnection.Connected -> "Recording"
                else -> "Waiting for the pendant"
            }
        val battery = status.battery?.let { " · $it%" } ?: ""
        return "$state$battery · ${usage.chunks} queued${syncSuffix(sync)}"
    }

    /** " · syncing 42%" while a sync runs and knows its size, " · syncing" before that. */
    private fun syncSuffix(sync: StorageSyncStatus?): String {
        if (sync == null) return ""
        return when (sync.state) {
            SyncState.Syncing, is SyncState.WaitingForUploads ->
                if (sync.runTotal >
                    0
                ) {
                    " · syncing ${(sync.runDone * PERCENT / sync.runTotal).coerceIn(0, PERCENT)}%"
                } else {
                    " · syncing"
                }

            else -> ""
        }
    }

    fun build(
        context: Context,
        status: CaptureStatus,
        usage: QueueUsage,
        sync: StorageSyncStatus? = null,
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
        val bookmark =
            PendingIntent.getService(
                context,
                2,
                Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_BOOKMARK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text(status, usage, sync))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(if (status.muted) R.string.unmute else R.string.mute), toggle)
            .addAction(0, context.getString(R.string.action_bookmark), bookmark)
            .build()
    }
}
