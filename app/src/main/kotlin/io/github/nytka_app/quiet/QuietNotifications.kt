package io.github.nytka_app.quiet

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.api.DayLevel
import kotlin.math.roundToInt

object QuietNotifications {
    const val CHANNEL = Notifier.AUDIO_LEVEL_CHANNEL
    private const val ID = 20_000

    /** How much quieter than usual, in whole dB; null when the day is quiet by its share of very quiet speech only. */
    fun decibelsUnder(level: DayLevel): Int? =
        (level.baselineMedianDbfs - level.wearerMedianDbfs).roundToInt().takeIf { it >= MIN_DB }

    fun text(
        context: Context,
        level: DayLevel,
    ): String =
        decibelsUnder(level)?.let { context.getString(R.string.quiet_day_text_db, it) }
            ?: context.getString(R.string.quiet_day_text_share, (level.quietShare * 100).roundToInt())

    fun build(
        context: Context,
        level: DayLevel,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val text = text(context, level)
        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(Notifier.ACCENT)
            .setContentTitle(context.getString(R.string.quiet_day_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    /** False without the notification permission. One id: a later notice replaces an earlier one. */
    fun post(
        context: Context,
        level: DayLevel,
    ): Boolean {
        if (!Notifier.canPost(context)) return false
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, level))
        return true
    }

    private const val MIN_DB = 10
}
