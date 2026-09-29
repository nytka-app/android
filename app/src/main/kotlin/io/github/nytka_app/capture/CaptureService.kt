package io.github.nytka_app.capture

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.BuildConfig
import io.github.nytka_app.alerts.Alert
import io.github.nytka_app.alerts.AlertInputs
import io.github.nytka_app.alerts.AlertMonitor
import io.github.nytka_app.alerts.AlertNotifications
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.diagnostics.AppLog
import io.github.nytka_app.core.diagnostics.DiagnosticsSink
import io.github.nytka_app.core.diagnostics.DiagnosticsUploader
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

    @Inject lateinit var diagnostics: DiagnosticsSink

    @Inject lateinit var appLog: AppLog

    @Inject lateinit var diagnosticsUploader: DiagnosticsUploader

    @Inject lateinit var hub: CaptureHub

    @Inject lateinit var pendants: PendantFactory

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: CaptureController? = null
    private var recorder: DiagnosticsRecorder? = null

    /** Lazy: [appLog] is injected after construction, and onDestroy may stop it before begin started it. */
    private val power by lazy { PowerBroadcasts(this, appLog) }
    private var started = false
    private var logRun = 0L

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
            ACTION_MUTE -> scope.launch { hub.setMuted(true, MuteSource.Notification) }
            ACTION_UNMUTE -> scope.launch { hub.setMuted(false, MuteSource.Notification) }
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
        logRun = appLog.start()
        power.start()
        val capture =
            CaptureController(
                pendants.create(scope, current.fakePendant),
                queue,
                StoreCaptureSettings(settings),
                scope,
                log = appLog,
            )
        controller = capture
        hub.attach(capture)
        capture.start(address)
        scope.launch { uploader.run() }
        recorder = startRecorder(capture)
        scope.launch { diagnosticsUploader.run() }
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

    private fun startRecorder(capture: CaptureController) =
        DiagnosticsRecorder(
            capture.status,
            queue.usage,
            uploader.state,
            diagnostics,
            scope,
            appVersion = BuildConfig.VERSION_NAME,
            device = "${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
            log = appLog,
        ).also { it.start() }

    override fun onDestroy() {
        power.stop()
        val capture = controller
        val diagnosticsRecorder = recorder
        val logGeneration = logRun
        hub.detach()
        applicationScope.launch {
            // The last sample is of the running capture, before stopping resets its status.
            // Whatever happens to it, the capture below still stops.
            runCatching { diagnosticsRecorder?.stop() }
            capture?.stop()
            appLog.stop(logGeneration)
            scope.cancel()
            // Alerts belong to a running capture: once it stops, none of them can clear on its own.
            Alert.entries.forEach { AlertNotifications.cancel(applicationContext, it) }
            UploadDrainWorker.drainNow(applicationContext)
            diagnosticsUploader.flushSafely()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_MUTE = "io.github.nytka_app.action.MUTE"
        const val ACTION_UNMUTE = "io.github.nytka_app.action.UNMUTE"
        private const val STATUS_SAMPLE_MS = 250L
        private const val ALERT_CHECK_MS = 30_000L

        fun start(context: Context) {
            // A connectedDevice foreground service needs BLUETOOTH_CONNECT; revoked in system settings, starting
            // would crash the app on every launch. The Device tab shows what is missing instead.
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return
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
