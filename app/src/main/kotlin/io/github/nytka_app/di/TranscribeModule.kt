package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.queue.QueueDatabase
import io.github.nytka_app.core.transcribe.DraftAudioTap
import io.github.nytka_app.core.transcribe.DraftTranscription
import io.github.nytka_app.core.transcribe.DraftsDao
import io.github.nytka_app.core.transcribe.TranscriberEngine
import io.github.nytka_app.transcribe.AndroidModelLibrary
import io.github.nytka_app.transcribe.AndroidTranscriptionBenchmark
import io.github.nytka_app.transcribe.ModelLibrary
import io.github.nytka_app.transcribe.SherpaEngine
import io.github.nytka_app.transcribe.TranscriptionBenchmark
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranscribeModule {
    /** sherpa-onnx in the play build; in the oss build a stand-in that says it is not there. */
    @Provides
    @Singleton
    fun engine(): TranscriberEngine = SherpaEngine()

    @Provides
    fun modelLibrary(library: AndroidModelLibrary): ModelLibrary = library

    @Provides
    fun benchmark(benchmark: AndroidTranscriptionBenchmark): TranscriptionBenchmark = benchmark

    @Provides
    fun draftsDao(database: QueueDatabase): DraftsDao = database.drafts()

    @Provides
    fun draftTranscription(dao: DraftsDao): DraftTranscription = DraftTranscription(dao)

    @Provides
    fun draftAudioTap(database: QueueDatabase): DraftAudioTap = DraftAudioTap(database)
}
