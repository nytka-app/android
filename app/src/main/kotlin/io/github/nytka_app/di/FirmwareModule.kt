package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.firmware.FirmwareNotices
import io.github.nytka_app.firmware.FirmwareReleases
import io.github.nytka_app.firmware.FirmwareUpdateChecker
import io.github.nytka_app.firmware.GithubFirmwareReleases
import okhttp3.OkHttpClient
import java.time.Clock
import javax.inject.Singleton

/** The pendant firmware notice; its own module, so `AppModule` stays as it is. */
@Module
@InstallIn(SingletonComponent::class)
object FirmwareModule {
    @Provides
    fun firmwareReleases(client: OkHttpClient): FirmwareReleases = GithubFirmwareReleases(client)

    @Provides
    @Singleton
    fun firmwareNotices(
        settings: SettingsSource,
        releases: FirmwareReleases,
        clock: Clock,
    ): FirmwareNotices = FirmwareUpdateChecker(settings, releases, clock)
}
