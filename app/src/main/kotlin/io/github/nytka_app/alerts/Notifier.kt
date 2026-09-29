package io.github.nytka_app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Notifier {
    const val RECORDING_CHANNEL = "recording"
    const val ALERTS_CHANNEL = "alerts"

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(RECORDING_CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(ALERTS_CHANNEL, "Alerts", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
