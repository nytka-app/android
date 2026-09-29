package io.github.nytka_app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueDatabase
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.Uploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun queueDatabase(
        @ApplicationContext context: Context,
    ): QueueDatabase = QueueDatabase.open(context)

    @Provides
    @Singleton
    fun frameQueue(database: QueueDatabase): FrameQueue = FrameQueue(database)

    @Provides
    @Singleton
    fun settingsStore(
        @ApplicationContext context: Context,
    ): SettingsStore = SettingsStore.create(context)

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // A redirect could carry audio to plain http or another host, past the ServerUrl check.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    @Provides
    @Singleton
    fun api(
        client: OkHttpClient,
        settings: SettingsStore,
    ): NytkaApi = NytkaApi(client) { settings.current() }

    @Provides
    @Singleton
    fun uploader(
        queue: FrameQueue,
        api: NytkaApi,
        settings: SettingsStore,
    ): Uploader = Uploader(queue, api, settings.settings)

    /** Outlives any one service instance: stopping capture finishes here after the service is gone. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
