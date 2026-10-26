package io.github.nytka_app.briefs

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.api.Nudge

object NudgeNotifications {
    const val CHANNEL = Notifier.NUDGES_CHANNEL
    private const val ID_BASE = 2_000_000
    private const val ID_SPAN = 1_000_000

    /** One notification per nudge id, so a repeat replaces rather than adds. */
    fun id(nudge: Nudge): Int = ID_BASE + (nudge.id.hashCode() and Int.MAX_VALUE) % ID_SPAN

    fun build(
        context: Context,
        nudge: Nudge,
    ): Notification {
        val link = Uri.parse(nudge.deepLink).takeIf { it.scheme == "nytka" }
        val open =
            PendingIntent.getActivity(
                context,
                id(nudge),
                Intent(context, MainActivity::class.java).apply { link?.let { data = it } },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        // The lock screen shows this version: no title, no name, no fact.
        val public =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.nudge_public_title))
                .build()
        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(Notifier.ACCENT)
            .setContentTitle(nudge.title)
            .setContentText(nudge.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    /** False without the notification permission. */
    fun post(
        context: Context,
        nudge: Nudge,
    ): Boolean {
        if (!Notifier.canPost(context)) return false
        context.getSystemService(NotificationManager::class.java).notify(id(nudge), build(context, nudge))
        return true
    }
}
