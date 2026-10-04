package io.github.nytka_app.capture

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.SQLException
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
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.diagnostics.AppLog
import io.github.nytka_app.core.diagnostics.DiagnosticsSink
import io.github.nytka_app.core.diagnostics.DiagnosticsUploader
import io.github.nytka_app.core.queue.BookmarkOutbox
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.ring.CaptureTimes
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.BookmarkUploader
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
import java.util.UUID
import javax.inject.Inject

/**
 * Holds the pendant connection while capture runs. Started by the companion service when the
 * pendant comes into range, by the app, or by first run after pairing.
 */
@AndroidEntryPoint
class CaptureService : LifecycleService() {
    @Inject
    lateinit var queue: FrameQueue

    @Inject
    lateinit var uploader: Uploader

    @Inject
    lateinit var bookmarkOutbox: BookmarkOutbox

    @Inject
    lateinit var bookmarkUploader: BookmarkUploader

    @Inject
    lateinit var settings: SettingsStore

    @Inject
    lateinit var diagnostics: DiagnosticsSink

    @Inject
    lateinit var appLog: AppLog

    @Inject
    lateinit var diagnosticsUploader: DiagnosticsUploader

    @Inject
    lateinit var hub: CaptureHub

    @Inject
    lateinit var pendants: PendantFactory

    @Inject
    lateinit var infoClient: InfoClient

    @Inject
    lateinit var captureTimes: CaptureTimes

    @Inject
    lateinit var muteLog: MuteLogRecorder

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: CaptureController? = null
    private var sync: StorageSyncController? = null
    private var recorder: DiagnosticsRecorder? = null

    /** Lazy: [appLog] is injected after construction, and onDestroy may stop it before begin started it. */
    private val power by lazy { PowerBroadcasts(this, appLog) }
    private var started = false
    private var pendingSyncAction: String? = null
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
            ACTION_BOOKMARK -> bookmarkNow()
            ACTION_SYNC_NOW, ACTION_STOP_SYNC -> syncAction(intent.action)
        }
        if (!started) {
            started = true
            scope.launch { begin() }
        }
        return START_STICKY
    }

    /** A bookmark at the phone's clock from the notification's action, with source `app`. */
    private fun bookmarkNow() {
        val at = System.currentTimeMillis()
        scope.launch {
            try {
                bookmarkOutbox.add(UUID.randomUUID().toString(), at, BookmarkOutbox.SOURCE_APP)
            } catch (e: SQLException) {
                appLog.w(TAG, "The bookmark outbox refused a write: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Before `begin` has attached the sync there is nothing to tell: the last such action waits for it. */
    private fun syncAction(action: String?) {
        if (sync == null) {
            pendingSyncAction = action
            return
        }
        if (action == ACTION_SYNC_NOW) hub.syncNow() else hub.stopSync()
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
        val pendant = pendants.create(scope, current.fakePendant, current.highPriorityConnection)
        val capture =
            CaptureController(
                pendant,
                queue,
                StoreCaptureSettings(settings),
                scope,
                log = appLog,
                muteLog = muteLog,
                bookmarks = bookmarkOutbox,
            )
        val storageSync =
            StorageSyncController(
                pendant,
                queue,
                queue.usage,
                infoClient,
                scope,
                address,
                times = captureTimes,
                log = appLog,
            )
        controller = capture
        sync = storageSync
        hub.attach(capture)
        hub.attachSync(storageSync)
        val pendantSettings = PendantSettingsController(pendant, scope)
        hub.attachSettings(pendantSettings)
        scope.launch { pendantSettings.state.collect(hub::publishSettings) }
        pendingSyncAction?.let(::syncAction)
        pendingSyncAction = null
        capture.start(address)
        storageSync.start()
        scope.launch { uploader.run() }
        scope.launch { bookmarkUploader.run() }
        recorder = startRecorder(capture, storageSync)
        scope.launch { diagnosticsUploader.run() }
        monitorAlerts(capture)
        scope.launch { capture.status.sample(STATUS_SAMPLE_MS).collect(hub::publish) }
        scope.launch { storageSync.status.sample(STATUS_SAMPLE_MS).collect(hub::publishSync) }
        combine(capture.status, queue.usage, storageSync.status) { status, usage, syncStatus ->
            CaptureNotification.text(status, usage, syncStatus) to Triple(status, usage, syncStatus)
        }.distinctUntilChanged { old, new -> old.first == new.first }
            .map { it.second }
            .collect { (status, usage, syncStatus) ->
                if (Notifier.canPost(this)) {
                    getSystemService(NotificationManager::class.java)
                        .notify(CaptureNotification.ID, CaptureNotification.build(this, status, usage, syncStatus))
                }
            }
    }

    private fun monitorAlerts(capture: CaptureController) =
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

    private fun startRecorder(
        capture: CaptureController,
        storageSync: StorageSyncController,
    ) = DiagnosticsRecorder(
        capture.status,
        queue.usage,
        uploader.state,
        diagnostics,
        scope,
        sync = storageSync.status,
        appVersion = BuildConfig.VERSION_NAME,
        device = "${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
        log = appLog,
    ).also { it.start() }

    override fun onDestroy() {
        power.stop()
        val capture = controller
        val storageSync = sync
        val diagnosticsRecorder = recorder
        val logGeneration = logRun
        hub.detach()
        applicationScope.launch {
            // The last sample is of the running capture, before stopping resets its status.
            // Whatever happens to it, the capture below still stops.
            runCatching { diagnosticsRecorder?.stop() }
            storageSync?.stop()
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
        const val ACTION_BOOKMARK = "io.github.nytka_app.action.BOOKMARK"
        const val ACTION_SYNC_NOW = "io.github.nytka_app.action.SYNC_NOW"
        const val ACTION_STOP_SYNC = "io.github.nytka_app.action.STOP_SYNC"
        private const val TAG = "CaptureService"
        private const val STATUS_SAMPLE_MS = 250L
        private const val ALERT_CHECK_MS = 30_000L

        fun start(
            context: Context,
            action: String? = null,
        ) {
            // A connectedDevice foreground service needs BLUETOOTH_CONNECT; revoked in system settings, starting
            // would crash the app on every launch. The Device tab shows what is missing instead.
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return
            ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java).setAction(action))
        }

        /** Starts the service if it is not running, and asks its sync to begin. */
        fun syncNow(context: Context) = start(context, ACTION_SYNC_NOW)

        fun stopSync(context: Context) = start(context, ACTION_STOP_SYNC)

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
