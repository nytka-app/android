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
            ),
        )
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
