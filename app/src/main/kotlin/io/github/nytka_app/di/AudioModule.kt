package io.github.nytka_app.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.AudioApi
import io.github.nytka_app.core.api.AudioClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.ui.conversations.AudioPlayer
import io.github.nytka_app.ui.conversations.ExoAudioPlayer
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    fun audioClient(api: NytkaApi): AudioClient = AudioApi(api)

    /** Not a singleton: each conversation screen owns its player and releases it. */
    @OptIn(UnstableApi::class)
    @Provides
    fun audioPlayer(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        audio: AudioClient,
    ): AudioPlayer = ExoAudioPlayer(context, client, audio)
}
