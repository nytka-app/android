package io.github.nytka_app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.nytka_app.R

object Notifier {
    const val RECORDING_CHANNEL = "recording"
    const val ALERTS_CHANNEL = "alerts"
    const val BRIEFS_CHANNEL = "briefs"
    const val AUDIO_LEVEL_CHANNEL = "audio-level"

    /** The design system's `primary` (indigo), which Android tints the small icon and action buttons with. */
    const val ACCENT = 0xFF33427E.toInt()

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    RECORDING_CHANNEL,
                    context.getString(R.string.recording_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    ALERTS_CHANNEL,
                    context.getString(R.string.alerts_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    BRIEFS_CHANNEL,
                    context.getString(R.string.brief_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    AUDIO_LEVEL_CHANNEL,
                    context.getString(R.string.quiet_day_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
