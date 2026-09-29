package io.github.nytka_app.capture

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.alerts.Alert
import io.github.nytka_app.alerts.AlertInputs
import io.github.nytka_app.alerts.AlertMonitor
import io.github.nytka_app.alerts.AlertNotifications
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.Uploader
import io.github.nytka_app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the pendant connection while capture runs. Started by the companion service when the
 * pendant comes into range, by the app, or by first run after pairing.
 */
@AndroidEntryPoint
class CaptureService : LifecycleService() {
    @Inject lateinit var queue: FrameQueue

    @Inject lateinit var uploader: Uploader

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var hub: CaptureHub

    @Inject lateinit var pendants: PendantFactory

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: CaptureController? = null
    private var started = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            CaptureNotification.ID,
            CaptureNotification.build(this, hub.status.value, queue.usage.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        when (intent?.action) {
            ACTION_MUTE -> scope.launch { hub.setMuted(true) }
            ACTION_UNMUTE -> scope.launch { hub.setMuted(false) }
        }
        if (!started) {
            started = true
            scope.launch { begin() }
        }
        return START_STICKY
    }

    @OptIn(FlowPreview::class)
    private suspend fun begin() {
        val current = settings.current()
        val address = if (current.fakePendant) PendantFactory.FAKE_ADDRESS else current.pendantAddress
        if (address == null) {
            stopSelf()
            return
        }
        val capture =
            CaptureController(pendants.create(scope, current.fakePendant), queue, StoreCaptureSettings(settings), scope)
        controller = capture
        hub.attach(capture)
        capture.start(address)
        scope.launch { uploader.run() }
        scope.launch {
            val monitor = AlertMonitor()
            while (true) {
                val inputs =
                    AlertInputs.of(
                        capture.status.value,
                        uploader.state.value,
                        queue.usage.value,
                        settings.current(),
                    )
                val change = monitor.evaluate(inputs, System.currentTimeMillis())
                change.started.forEach { AlertNotifications.post(this@CaptureService, it, inputs) }
                change.cleared.forEach { AlertNotifications.cancel(this@CaptureService, it) }
                delay(ALERT_CHECK_MS)
            }
        }
        scope.launch { capture.status.sample(STATUS_SAMPLE_MS).collect(hub::publish) }
        combine(capture.status, queue.usage) { status, usage ->
            CaptureNotification.text(status, usage) to
                (status to usage)
        }.distinctUntilChanged { old, new -> old.first == new.first }
            .map { it.second }
            .collect { (status, usage) ->
                if (Notifier.canPost(this)) {
                    getSystemService(NotificationManager::class.java)
                        .notify(CaptureNotification.ID, CaptureNotification.build(this, status, usage))
                }
            }
    }

    override fun onDestroy() {
        val capture = controller
        hub.detach()
        applicationScope.launch {
            capture?.stop()
            scope.cancel()
            // Alerts belong to a running capture: once it stops, none of them can clear on its own.
            Alert.entries.forEach { AlertNotifications.cancel(applicationContext, it) }
            UploadDrainWorker.drainNow(applicationContext)
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_MUTE = "io.github.nytka_app.action.MUTE"
        const val ACTION_UNMUTE = "io.github.nytka_app.action.UNMUTE"
        private const val STATUS_SAMPLE_MS = 250L
        private const val ALERT_CHECK_MS = 30_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }

        /** After the pendant or the fake-pendant switch changed. */
        fun restart(context: Context) {
            stop(context)
            start(context)
        }
    }
}
