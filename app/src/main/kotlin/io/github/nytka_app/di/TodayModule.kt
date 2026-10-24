package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.InsightsApi
import io.github.nytka_app.core.api.InsightsClient
import io.github.nytka_app.core.api.ItemMarksApi
import io.github.nytka_app.core.api.ItemMarksClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.TodayApi
import io.github.nytka_app.core.api.TodayClient

@Module
@InstallIn(SingletonComponent::class)
object TodayModule {
    @Provides
    fun todayClient(api: NytkaApi): TodayClient = TodayApi(api)

    @Provides
    fun insightsClient(api: NytkaApi): InsightsClient = InsightsApi(api)

    @Provides
    fun itemMarksClient(api: NytkaApi): ItemMarksClient = ItemMarksApi(api)
}
