package io.github.nytka_app.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.CardsClient
import io.github.nytka_app.ui.people.cards.ClipPlayer
import io.github.nytka_app.ui.people.cards.ExoClipPlayer
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ClipModule {
    /** Not a singleton: the cards' view model owns its player and releases it. */
    @OptIn(UnstableApi::class)
    @Provides
    fun clipPlayer(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        cards: CardsClient,
    ): ClipPlayer = ExoClipPlayer(context, client, cards)
}
