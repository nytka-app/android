package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.VoiceprintsApi
import io.github.nytka_app.core.api.VoiceprintsClient

@Module
@InstallIn(SingletonComponent::class)
object VoiceprintsModule {
    @Provides
    fun voiceprintsClient(api: NytkaApi): VoiceprintsClient = VoiceprintsApi(api)
}
