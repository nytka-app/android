package io.github.nytka_app.briefs

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.api.UpcomingBrief
import io.github.nytka_app.ui.conversations.Formatting
import java.time.ZoneId

object BriefNotifications {
    const val CHANNEL = Notifier.BRIEFS_CHANNEL
    private const val ID_BASE = 10_000
    private const val ID_SPAN = 1_000_000

    /** One notification per brief, so a repeat of the same brief replaces rather than adds. */
    fun id(brief: UpcomingBrief): Int =
        ID_BASE + (
            brief.brief
                ?.id
                .orEmpty()
                .hashCode() and Int.MAX_VALUE
        ) % ID_SPAN

    fun build(
        context: Context,
        brief: UpcomingBrief,
        zone: ZoneId,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val start = Formatting.parse(brief.startsAt)?.let { Formatting.clock(it, zone) }
        val text = brief.brief?.text.orEmpty()
        // The lock screen shows this version: no title, no name, no fact.
        val public =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.brief_public_title))
                .build()
        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.brief_title, brief.title))
            .setContentText(start?.let { context.getString(R.string.brief_starts_at, it) } ?: text)
            .setStyle(
                NotificationCompat
                    .BigTextStyle()
                    .setSummaryText(start?.let { context.getString(R.string.brief_starts_at, it) })
                    .bigText(text),
            ).setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    /** False without the notification permission. */
    fun post(
        context: Context,
        brief: UpcomingBrief,
        zone: ZoneId,
    ): Boolean {
        if (!Notifier.canPost(context)) return false
        context.getSystemService(NotificationManager::class.java).notify(id(brief), build(context, brief, zone))
        return true
    }
}
