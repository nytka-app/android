package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.SpeechApi
import io.github.nytka_app.core.api.SpeechClient

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {
    @Provides
    fun speechClient(api: NytkaApi): SpeechClient = SpeechApi(api)
}
