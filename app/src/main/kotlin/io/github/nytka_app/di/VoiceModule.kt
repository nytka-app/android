package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.EnrollmentCapture
import io.github.nytka_app.capture.HubEnrollmentCapture
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.VoiceApi
import io.github.nytka_app.core.api.VoiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    fun voiceClient(api: NytkaApi): VoiceClient = VoiceApi(api)

    @Provides
    @Singleton
    fun enrollmentCapture(hub: CaptureHub): EnrollmentCapture =
        HubEnrollmentCapture(hub, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
