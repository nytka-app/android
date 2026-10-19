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

    /** The title is the state word, so it leads: "Recording", "Muted", "Waiting for pendant". */
    fun state(status: CaptureStatus): CaptureState = CaptureState.of(status)

    /** The line under the title: "82% · 0 queued", led by the reason when the pendant was refused. */
    fun text(
        status: CaptureStatus,
        usage: QueueUsage,
        sync: StorageSyncStatus? = null,
    ): String {
        val refused = (status.connection as? PendantConnection.Refused)?.let { "Pendant not supported · " } ?: ""
        val battery = status.battery?.let { "$it% · " } ?: ""
        return "$refused$battery${usage.chunks} queued${syncSuffix(sync)}"
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
        val state = state(status)
        return NotificationCompat
            .Builder(context, CHANNEL)
            // Android tints the small icon, so the glyph's shape alone tells the states apart.
            .setSmallIcon(state.iconRes)
            .setContentTitle(context.getString(state.wordRes))
            .setContentText(text(status, usage, sync))
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
