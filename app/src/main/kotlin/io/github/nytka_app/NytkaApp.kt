package io.github.nytka_app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.briefs.BriefWorker
import io.github.nytka_app.capture.UploadDrainWorker
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.quiet.QuietDayWorker
import io.github.nytka_app.transcribe.PhoneTranscriptionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NytkaApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject
    lateinit var settings: SettingsSource

    @Inject
    lateinit var phoneTranscription: PhoneTranscriptionController

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        UploadDrainWorker.schedule(this)
        QuietDayWorker.schedule(this)
        phoneTranscription.start()
        // KEEP makes this a no-op when the job is already queued.
        CoroutineScope(Dispatchers.Default).launch {
            val current = settings.current()
            if (current.briefNotifications || current.nudgeNotifications) BriefWorker.schedule(this@NytkaApp)
        }
    }
}
