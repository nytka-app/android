package io.github.nytka_app.capture

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Exists only so that Android lets [NowPlayingWatcher] see the active media sessions: notification access is what
 * grants that. It reads no notification; Android binds it whenever the access is on.
 */
class NowPlayingListener : NotificationListenerService() {
    companion object {
        fun component(context: Context) = ComponentName(context, NowPlayingListener::class.java)

        fun granted(context: Context) =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
