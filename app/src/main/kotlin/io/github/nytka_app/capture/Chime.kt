package io.github.nytka_app.capture

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** A short sound that tells the people nearby the pendant is recording. */
fun interface Chime {
    fun play()
}

/**
 * A system tone on the notification stream: no asset, no microphone, no permission. It follows the notification
 * volume, so silent mode, and Do Not Disturb when it filters notifications, make it inaudible.
 */
class ToneChime(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Chime {
    override fun play() {
        // The constructor throws when the audio system has no free tone resource; a missed chime is not worth a crash.
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME) }.getOrNull() ?: return
        tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
        handler.postDelayed({ tone.release() }, RELEASE_MS)
    }

    private companion object {
        const val VOLUME = 80
        const val TONE_MS = 300
        const val RELEASE_MS = 600L
    }
}
