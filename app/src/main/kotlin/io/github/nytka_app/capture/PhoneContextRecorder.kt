package io.github.nytka_app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.nytka_app.core.context.Activity
import io.github.nytka_app.core.context.ContextRange
import io.github.nytka_app.core.context.ContextRangeTracker
import io.github.nytka_app.core.context.MediaCategory
import io.github.nytka_app.core.context.Mode
import io.github.nytka_app.core.context.Route
import io.github.nytka_app.core.context.Usage
import io.github.nytka_app.core.queue.ContextRow
import io.github.nytka_app.core.queue.ContextSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Feeds the [ContextRangeTracker] from `AudioManager` callbacks and the screen broadcasts between [start] and [stop]:
 * which kinds of players are active, the call mode, the communication device, whether media goes to headphones and
 * whether the screen is on. It needs no permission, never polls (the volume and the routes are read inside a
 * callback, and the one timer is the tracker's grace after the sound stops) and keeps no app name, title or number:
 * a range is a kind, a route and two times. The capture service starts and stops it.
 *
 * Two extras run only while their switch is on ([setExtras]): activity transitions from [activities] (a permission)
 * and what kind of app plays a media session, from [nowPlaying] (notification access). Each adds a one-word detail
 * from a fixed list, never a name.
 */
class PhoneContextRecorder(
    private val context: Context,
    private val sink: ContextSink,
    private val writes: CoroutineScope,
    private val activities: ActivityWatcher = TransitionWatcher(context),
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val executor = ContextCompat.getMainExecutor(context)

    // The tracker runs on elapsed time, which only moves forward; a range's wall-clock times come from the offset.
    private val tracker =
        ContextRangeTracker(SystemClock::elapsedRealtime) { range -> store(range) }
    private var running = false
    private val timer = Runnable { tracker.onTimer() }
    private val playback =
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) = feedPlayback(configs)
        }
    private val modes = AudioManager.OnModeChangedListener { feedMode(it) }
    private val communication = AudioManager.OnCommunicationDeviceChangedListener { feedCommunication(it) }
    private val devices =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = feedPlayback(null)

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = feedPlayback(null)
        }
    private val screen =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = tracker.onScreen(intent.action == Intent.ACTION_SCREEN_ON)
        }
    private var lastUsages: Set<Usage> = emptySet()
    private val nowPlaying = NowPlayingWatcher(context, main)
    private var lastPlaying: MediaCategory? = null
    private var wantActivity = false
    private var wantNowPlaying = false

    /** Registers the callbacks and takes the call mode as it is, so a call already running is not missed. */
    fun start() {
        main.post {
            if (running) return@post
            running = true
            audio.registerAudioPlaybackCallback(playback, main)
            audio.registerAudioDeviceCallback(devices, main)
            audio.addOnModeChangedListener(executor, modes)
            audio.addOnCommunicationDeviceChangedListener(executor, communication)
            val screenEvents = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
            ContextCompat.registerReceiver(
                context,
                screen,
                screenEvents,
                null,
                main,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            tracker.onScreen(power.isInteractive)
            feedCommunication(audio.communicationDevice)
            feedMode(audio.mode)
            feedPlayback(null)
            applyExtras()
        }
    }

    /** The activity and now-playing switches; each runs only while the recorder does and its switch is on. */
    fun setExtras(
        activity: Boolean,
        playing: Boolean,
    ) {
        main.post {
            wantActivity = activity
            wantNowPlaying = playing
            if (running) applyExtras()
        }
    }

    private fun applyExtras() {
        if (wantActivity) {
            activities.start { activity, atMs -> main.post { feedActivity(activity, atMs) } }
        } else {
            activities.stop()
            tracker.onActivity(null)
        }
        if (wantNowPlaying) nowPlaying.start(::feedNowPlaying) else nowPlaying.stop()
    }

    /** Unregisters everything and closes what is open at this moment. */
    fun stop() {
        main.post {
            if (!running) return@post
            running = false
            audio.unregisterAudioPlaybackCallback(playback)
            audio.unregisterAudioDeviceCallback(devices)
            audio.removeOnModeChangedListener(modes)
            audio.removeOnCommunicationDeviceChangedListener(communication)
            context.unregisterReceiver(screen)
            main.removeCallbacks(timer)
            activities.stop()
            nowPlaying.stop()
            tracker.onStop()
        }
    }

    private fun feedPlayback(configs: List<AudioPlaybackConfiguration>?) {
        // Notification access may have been granted since the switch went on; a player starting is when it matters.
        if (running && wantNowPlaying && !nowPlaying.listening) nowPlaying.start(::feedNowPlaying)
        // A device change brings no player list: the last one still says what plays.
        if (configs != null) lastUsages = configs.mapTo(mutableSetOf()) { usageOf(it.audioAttributes.usage) }
        val outputs = mediaOutputs()
        tracker.onPlayback(lastUsages, onSpeaker(outputs), audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        tracker.onHeadphones(headphonesOf(outputs))
        tracker.onNowPlaying(lastPlaying, routeOf(outputs))
        scheduleTimer()
    }

    private fun feedActivity(
        activity: Activity?,
        atMs: Long,
    ) {
        if (running && wantActivity) tracker.onActivity(activity, atMs)
    }

    private fun feedNowPlaying(category: MediaCategory?) {
        lastPlaying = category
        tracker.onNowPlaying(category, routeOf(mediaOutputs()))
        scheduleTimer()
    }

    private fun feedMode(mode: Int) {
        tracker.onMode(modeOf(mode))
        scheduleTimer()
    }

    private fun feedCommunication(device: AudioDeviceInfo?) = tracker.onCommunicationDevice(routeOf(device?.type))

    private fun scheduleTimer() {
        main.removeCallbacks(timer)
        tracker.pendingCloseAtMs?.let { main.postDelayed(timer, (it - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
    }

    private fun store(range: ContextRange) {
        // Elapsed times to epoch: the offset is read once per range, when it closes.
        val offset = wallClock() - SystemClock.elapsedRealtime()
        val row =
            ContextRow(
                UUID.randomUUID().toString(),
                range.kind.wire,
                range.route.wire,
                range.startMs + offset,
                range.endMs + offset,
                range.detail,
            )
        writes.launch { sink.add(row) }
    }

    /** Where music would play now; before Android 13, every connected output. */
    private fun mediaOutputs(): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audio.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }

    private companion object {
        val WIRED_HEADPHONES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )

        // An A2DP output may be headphones, a speaker or a car; AudioDeviceInfo does not say, so it counts as headphones.
        val BLUETOOTH_HEADPHONES =
            setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )

        val EXTERNAL_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )

        /** The built-in speaker and nothing external. */
        fun onSpeaker(outputs: List<AudioDeviceInfo>) =
            outputs.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } &&
                outputs.none { it.type in EXTERNAL_TYPES }

        /** Where media plays: the speaker, headphones, or something else (a TV, a USB or HDMI output). */
        fun routeOf(outputs: List<AudioDeviceInfo>) =
            if (onSpeaker(outputs)) {
                Route.SPEAKER
            } else {
                headphonesOf(outputs)
                    ?: Route.OTHER
            }

        fun headphonesOf(outputs: List<AudioDeviceInfo>) =
            when {
                outputs.any { it.type in WIRED_HEADPHONES } -> Route.HEADSET
                outputs.any { it.type in BLUETOOTH_HEADPHONES } -> Route.BLUETOOTH
                else -> null
            }

        fun usageOf(usage: Int) =
            when (usage) {
                AudioAttributes.USAGE_MEDIA -> Usage.MEDIA
                AudioAttributes.USAGE_GAME -> Usage.GAME
                AudioAttributes.USAGE_UNKNOWN -> Usage.UNKNOWN
                else -> Usage.OTHER
            }

        fun modeOf(mode: Int) =
            when (mode) {
                AudioManager.MODE_NORMAL -> Mode.NORMAL
                AudioManager.MODE_IN_CALL -> Mode.IN_CALL
                AudioManager.MODE_IN_COMMUNICATION -> Mode.IN_COMMUNICATION
                AudioManager.MODE_CALL_REDIRECT -> Mode.CALL_REDIRECT
                AudioManager.MODE_COMMUNICATION_REDIRECT -> Mode.COMMUNICATION_REDIRECT
                else -> Mode.OTHER
            }

        fun routeOf(type: Int?) =
            when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Route.SPEAKER
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Route.EARPIECE
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> Route.HEADSET

                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID,
                -> Route.BLUETOOTH

                else -> Route.OTHER
            }
    }
}
