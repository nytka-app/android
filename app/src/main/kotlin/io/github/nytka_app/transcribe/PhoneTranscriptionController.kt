package io.github.nytka_app.transcribe

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.core.transcribe.DraftAudioTap
import io.github.nytka_app.core.transcribe.TranscriberEngine
import io.github.nytka_app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the audio copy and the job in step with "Transcribe on this phone": both run only while it is on. */
@Singleton
class PhoneTranscriptionController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsSource,
        private val tap: DraftAudioTap,
        private val engine: TranscriberEngine,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch {
                settings.settings
                    .map { it.phoneTranscription && engine.available }
                    .distinctUntilChanged()
                    .collect { on ->
                        tap.sync(on)
                        if (on) DraftTranscriptionWorker.schedule(context) else DraftTranscriptionWorker.cancel(context)
                    }
            }
        }
    }
