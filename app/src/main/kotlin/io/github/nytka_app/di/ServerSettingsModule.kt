package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.ServerSettingsApi
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.ui.device.PhoneZone
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object ServerSettingsModule {
    @Provides
    fun serverSettingsClient(api: NytkaApi): ServerSettingsClient = ServerSettingsApi(api)

    @Provides
    fun phoneZone(): PhoneZone = PhoneZone { ZoneId.systemDefault().id }
}
