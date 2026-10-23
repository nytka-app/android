package io.github.nytka_app.capture

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import io.github.nytka_app.core.context.MediaCategory

/**
 * Hears which media sessions play, through notification access ([NowPlayingListener]). It reads only the package of
 * the session that plays and turns it into a [MediaCategory] at once: no title, artist or app name is kept.
 */
class NowPlayingWatcher(
    private val context: Context,
    private val handler: Handler,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private var onChange: ((MediaCategory?) -> Unit)? = null
    private var controllers: List<MediaController> = emptyList()
    private val state =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = report()

            override fun onSessionDestroyed() = report()
        }
    private val active = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    val listening: Boolean get() = onChange != null

    /** Starts listening if notification access is on; returns whether it listens. */
    fun start(onChange: (MediaCategory?) -> Unit): Boolean {
        if (this.onChange != null) return true
        if (!NowPlayingListener.granted(context)) return false
        val component = NowPlayingListener.component(context)
        try {
            sessions.addOnActiveSessionsChangedListener(active, component, handler)
            this.onChange = onChange
            attach(sessions.getActiveSessions(component))
        } catch (_: SecurityException) {
            // The access was switched off between the check and the call.
            stop()
            return false
        }
        return true
    }

    fun stop() {
        sessions.removeOnActiveSessionsChangedListener(active)
        controllers.forEach { it.unregisterCallback(state) }
        controllers = emptyList()
        onChange?.invoke(null)
        onChange = null
    }

    private fun attach(next: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(state) }
        controllers = next
        controllers.forEach { it.registerCallback(state, handler) }
        report()
    }

    private fun report() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        onChange?.invoke(playing?.packageName?.let(MediaCategory::of))
    }
}
