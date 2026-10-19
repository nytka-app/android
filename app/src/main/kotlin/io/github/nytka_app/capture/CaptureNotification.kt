package io.github.nytka_app.capture

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
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

    /** The title is the state word, so it leads: "Recording", "Muted", "Waiting for pendant". */
    fun state(status: CaptureStatus): CaptureState = CaptureState.of(status)

    /** The line under the title: "82% · 0 queued", led by the reason when the pendant was refused. */
    fun text(
        resources: Resources,
        status: CaptureStatus,
        usage: QueueUsage,
        sync: StorageSyncStatus? = null,
    ): String {
        val refused =
            if (status.connection is PendantConnection.Refused) {
                resources.getString(
                    R.string.notification_refused_prefix,
                )
            } else {
                ""
            }
        val battery = status.battery?.let { resources.getString(R.string.notification_battery_prefix, it) } ?: ""
        val queued = resources.getQuantityString(R.plurals.notification_queued_format, usage.chunks, usage.chunks)
        return "$refused$battery$queued${syncSuffix(resources, sync)}"
    }

    /** " · syncing 42%" while a sync runs and knows its size, " · syncing" before that. */
    private fun syncSuffix(
        resources: Resources,
        sync: StorageSyncStatus?,
    ): String {
        if (sync == null) return ""
        return when (sync.state) {
            SyncState.Syncing, is SyncState.WaitingForUploads ->
                if (sync.runTotal > 0) {
                    val percent = (sync.runDone * PERCENT / sync.runTotal).coerceIn(0, PERCENT).toInt()
                    resources.getString(R.string.notification_syncing_percent_format, percent)
                } else {
                    resources.getString(R.string.notification_syncing)
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
        val state = state(status)
        return NotificationCompat
            .Builder(context, CHANNEL)
            // Android tints the small icon, so the glyph's shape alone tells the states apart.
            .setSmallIcon(state.iconRes)
            .setContentTitle(context.getString(state.wordRes))
            .setContentText(text(context.resources, status, usage, sync))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(if (status.muted) R.string.unmute else R.string.mute), toggle)
            .addAction(0, context.getString(R.string.action_open), open)
            .addAction(0, context.getString(R.string.action_bookmark), bookmark)
            .build()
    }
}
