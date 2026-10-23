package io.github.nytka_app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.phonemic.AndroidPhoneMicControls
import io.github.nytka_app.phonemic.PhoneMicControls

@Module
@InstallIn(SingletonComponent::class)
object PhoneMicModule {
    @Provides
    fun phoneMicControls(
        @ApplicationContext context: Context,
    ): PhoneMicControls = AndroidPhoneMicControls(context)
}
