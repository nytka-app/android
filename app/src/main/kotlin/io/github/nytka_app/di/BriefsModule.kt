package io.github.nytka_app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.briefs.BriefScheduler
import io.github.nytka_app.briefs.WorkBriefScheduler
import io.github.nytka_app.core.api.BriefsApi
import io.github.nytka_app.core.api.BriefsClient
import io.github.nytka_app.core.api.NytkaApi

@Module
@InstallIn(SingletonComponent::class)
object BriefsModule {
    @Provides
    fun briefsClient(api: NytkaApi): BriefsClient = BriefsApi(api)

    @Provides
    fun briefScheduler(
        @ApplicationContext context: Context,
    ): BriefScheduler = WorkBriefScheduler(context)
}
