package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.CardsApi
import io.github.nytka_app.core.api.CardsClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.PeopleApi
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.PersonPageApi
import io.github.nytka_app.core.api.PersonPageClient
import io.github.nytka_app.core.api.ReviewApi
import io.github.nytka_app.core.api.ReviewClient

@Module
@InstallIn(SingletonComponent::class)
object PeopleModule {
    @Provides
    fun peopleClient(api: NytkaApi): PeopleClient = PeopleApi(api)

    @Provides
    fun personPageClient(api: NytkaApi): PersonPageClient = PersonPageApi(api)

    @Provides
    fun reviewClient(api: NytkaApi): ReviewClient = ReviewApi(api)

    @Provides
    fun cardsClient(api: NytkaApi): CardsClient = CardsApi(api)
}
