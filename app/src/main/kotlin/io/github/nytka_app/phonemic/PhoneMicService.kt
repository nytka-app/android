package io.github.nytka_app.phonemic

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.core.upload.Uploader
import io.github.nytka_app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Records the phone's microphone while the user keeps "Phone microphone" on: a foreground service of type
 * `microphone` with an ongoing notification and its Stop action. One run is one capture session of source
 * `phone-mic`, sequence numbers from 0 without holes, never mixed with the pendant's. It is started only from the
 * Device screen, never by boot, the companion service or a schedule, and is not restarted after Android stops it.
 * The mute switch and the mute windows pause it. Frames go into the same Room queue as the pendant's, are sealed
 * every 30 s and uploaded by the shared [Uploader].
 */
@AndroidEntryPoint
class PhoneMicService : Service() {
    @Inject
    lateinit var queue: FrameQueue

    @Inject
    lateinit var uploader: Uploader

    @Inject
    lateinit var settings: SettingsSource

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val session = UUID.randomUUID()
    private var seq = 0L

    // One writer keeps the frames in the order they were read.
    private val packets = Channel<Pair<ByteArray, Long>>(Channel.UNLIMITED)
    private val recorder by lazy {
        PhoneMicRecorder(this, onPacket = { packet, at -> packets.trySend(packet to at) }, onFailure = ::failed)
    }
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ServiceCompat.startForeground(
            this,
            ID,
            notification(paused = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        scope.launch {
            for ((packet, at) in packets) queue.addPhoneMic(session, seq++, at, packet)
        }
        scope.launch {
            muted().collect { paused ->
                if (paused) {
                    recorder.stop()
                    PhoneMicState.set(PhoneMicStatus.Paused)
                } else if (recorder.start()) {
                    PhoneMicState.set(PhoneMicStatus.Recording)
                } else {
                    failed(getString(R.string.phone_mic_cannot_start))
                    return@collect
                }
                // Starting in the foreground again replaces the notice; it needs no notification permission.
                ServiceCompat.startForeground(
                    this@PhoneMicService,
                    ID,
                    notification(paused),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            }
        }
        scope.launch {
            while (true) {
                delay(SEAL_EVERY_MS)
                queue.seal()
                uploader.drain()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun muted(): Flow<Boolean> {
        val manual = settings.settings.map { it.muted }
        val scheduled =
            settings.settings.map { it.muteSchedule }.distinctUntilChanged().flatMapLatest { schedule ->
                flow {
                    while (true) {
                        val at = Instant.now().atZone(ZoneId.systemDefault())
                        emit(schedule.mutedAt(at))
                        delay(minOf(schedule.untilChange(at)?.toMillis() ?: RECHECK_MS, RECHECK_MS))
                    }
                }
            }
        return combine(manual, scheduled) { a, b -> a || b }.distinctUntilChanged()
    }

    private fun failed(reason: String) {
        PhoneMicState.set(PhoneMicStatus.Failed(reason))
        stopSelf()
    }

    override fun onDestroy() {
        recorder.stop()
        packets.close()
        scope.cancel()
        if (PhoneMicState.status.value !is PhoneMicStatus.Failed) PhoneMicState.set(PhoneMicStatus.Off)
        // What was read is in the channel or in Room; seal and send it after the service is gone.
        applicationScope.launch {
            queue.seal()
            uploader.drain()
        }
        super.onDestroy()
    }

    private fun notification(paused: Boolean): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, PhoneMicService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, Notifier.PHONE_MIC_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(Notifier.ACCENT)
            .setContentTitle(getString(if (paused) R.string.phone_mic_paused else R.string.phone_mic_recording))
            .setContentText(getString(R.string.phone_mic_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.phone_mic_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ID = 3
        const val ACTION_STOP = "io.github.nytka_app.action.STOP_PHONE_MIC"
        private const val SEAL_EVERY_MS = 30_000L
        private const val RECHECK_MS = 60_000L
    }
}
