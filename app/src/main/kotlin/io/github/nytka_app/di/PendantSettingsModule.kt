package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.HubPendantSettingsControls
import io.github.nytka_app.capture.PendantSettingsControls
import javax.inject.Singleton

/** What the Device tab uses to reach the pendant's settings; its own module, so `AppModule` stays as it is. */
@Module
@InstallIn(SingletonComponent::class)
object PendantSettingsModule {
    @Provides
    @Singleton
    fun pendantSettings(hub: CaptureHub): PendantSettingsControls = HubPendantSettingsControls(hub)
}
