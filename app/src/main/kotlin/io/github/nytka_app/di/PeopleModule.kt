package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.PeopleApi
import io.github.nytka_app.core.api.PeopleClient

@Module
@InstallIn(SingletonComponent::class)
object PeopleModule {
    @Provides
    fun peopleClient(api: NytkaApi): PeopleClient = PeopleApi(api)
}
