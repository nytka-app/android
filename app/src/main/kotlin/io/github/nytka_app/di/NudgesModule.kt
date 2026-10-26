package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NudgesApi
import io.github.nytka_app.core.api.NudgesClient
import io.github.nytka_app.core.api.NytkaApi

@Module
@InstallIn(SingletonComponent::class)
object NudgesModule {
    @Provides
    fun nudgesClient(api: NytkaApi): NudgesClient = NudgesApi(api)
}
