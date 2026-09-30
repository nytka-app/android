package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.AskApi
import io.github.nytka_app.core.api.AskClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.settings.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AskModule {
    /** The server's model call may take two minutes; only this client waits that long. */
    private const val READ_TIMEOUT_SECONDS = 150L

    @Provides
    fun askClient(
        client: OkHttpClient,
        settings: SettingsStore,
    ): AskClient =
        AskApi(
            NytkaApi(client.newBuilder().readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()) {
                settings.current()
            },
        )
}
