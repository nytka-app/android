package io.github.nytka_app.ui.transcribe

import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.transcribe.DraftAudioRow
import io.github.nytka_app.core.transcribe.DraftTextRow
import io.github.nytka_app.core.transcribe.DraftsDao
import io.github.nytka_app.core.transcribe.ModelFileEntry
import io.github.nytka_app.core.transcribe.ModelFiles
import io.github.nytka_app.core.transcribe.ModelLayout
import io.github.nytka_app.core.transcribe.OnDeviceTranscriber
import io.github.nytka_app.core.transcribe.TranscriberEngine
import io.github.nytka_app.transcribe.BenchmarkResult
import io.github.nytka_app.transcribe.InstalledModel
import io.github.nytka_app.transcribe.ModelImport
import io.github.nytka_app.transcribe.ModelLibrary
import io.github.nytka_app.transcribe.TranscriptionBenchmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneTranscriptionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val parakeet = InstalledModel("parakeet", ModelLayout.Transducer, 670_000_000)

    private class FakeLibrary(
        model: InstalledModel? = null,
    ) : ModelLibrary {
        val state = MutableStateFlow(model)
        var inspected: ModelImport? = null
        val installs = mutableListOf<ModelImport>()

        override val installed: StateFlow<InstalledModel?> = state

        override suspend fun inspect(folder: String) = inspected ?: throw IllegalArgumentException("no model")

        override suspend fun install(
            import: ModelImport,
            progress: (Long) -> Unit,
        ) {
            installs += import
            progress(import.bytes)
            state.value = InstalledModel(import.name, import.layout, import.bytes)
        }

        override suspend fun delete() {
            state.value = null
        }

        override fun current(): ModelFiles? = null
    }

    private class FakeDrafts : DraftsDao {
        val frames = MutableStateFlow(0L)
        val texts = MutableStateFlow(emptyList<DraftTextRow>())

        override suspend fun oldestAudio(limit: Int) = emptyList<DraftAudioRow>()

        override fun audioCount() = frames.map { if (it > 0) 1 else 0 }

        override fun audioFrames() = frames

        override suspend fun deleteAllAudio() = Unit

        override suspend fun insertTexts(rows: List<DraftTextRow>) {
            texts.value += rows
        }

        override suspend fun deleteAudio(ids: List<Long>) = Unit

        override fun newestTexts(limit: Int) = texts

        override suspend fun deleteTextsBefore(cutoffMs: Long) = Unit

        override suspend fun deleteAllTexts() {
            texts.value = emptyList()
        }
    }

    private class FakeEngine(
        override val available: Boolean = true,
    ) : TranscriberEngine {
        override val layouts = setOf(ModelLayout.Transducer)

        override fun open(model: ModelFiles): OnDeviceTranscriber = error("not in these tests")
    }

    private class FakeBenchmark(
        private val result: BenchmarkResult?,
    ) : TranscriptionBenchmark {
        override suspend fun run() = result ?: error("cannot load")
    }

    private val settings = FakeSettings()
    private val library = FakeLibrary()
    private val drafts = FakeDrafts()

    private fun viewModel(
        library: ModelLibrary = this.library,
        engine: TranscriberEngine = FakeEngine(),
        benchmark: TranscriptionBenchmark = FakeBenchmark(null),
    ) = PhoneTranscriptionViewModel(settings, library, drafts, engine, benchmark)

    private fun import(freeBytes: Long) =
        ModelImport(
            folder = "content://tree/parakeet",
            name = "parakeet",
            files = listOf(ModelFileEntry("encoder.int8.onnx", 650_000_000), ModelFileEntry("tokens.txt", 100_000)),
            layout = ModelLayout.Transducer,
            freeBytes = freeBytes,
        )

    @Test
    fun `a build without an engine says so`() {
        val model = viewModel(engine = FakeEngine(available = false))

        assertFalse(model.state.value.available)
    }

    @Test
    fun `the switch stays off without a model and turns on with one`() {
        val model = viewModel()

        model.setOn(true)
        assertFalse(settings.state.value.phoneTranscription)

        library.state.value = parakeet
        model.setOn(true)
        assertTrue(settings.state.value.phoneTranscription)
        assertTrue(model.state.value.on)
    }

    @Test
    fun `a picked folder is shown with its size before anything is copied`() {
        library.inspected = import(freeBytes = 10_000_000_000)
        val model = viewModel()

        model.folderPicked("content://tree/parakeet")

        assertEquals(
            650_100_000L,
            model.state.value.prompt
                ?.bytes,
        )
        assertTrue(library.installs.isEmpty())

        model.confirmImport()

        assertEquals(1, library.installs.size)
        assertNull(model.state.value.prompt)
        assertNull(model.state.value.importedBytes)
        assertEquals(
            "parakeet",
            model.state.value.model
                ?.name,
        )
    }

    @Test
    fun `a folder without a model gets a sentence, not the system's message`() {
        val model = viewModel()

        model.folderPicked("content://tree/photos")

        assertEquals(TranscriptionNotice.NoModelInFolder, model.state.value.notice)
        assertNull(model.state.value.prompt)
    }

    @Test
    fun `a model that does not fit is not copied`() {
        library.inspected = import(freeBytes = 700_000_000)
        val model = viewModel()

        model.folderPicked("content://tree/parakeet")
        model.confirmImport()

        assertTrue(library.installs.isEmpty())
        assertEquals(TranscriptionNotice.NoRoom, model.state.value.notice)
    }

    @Test
    fun `deleting the model switches recognition off`() {
        library.state.value = parakeet
        settings.state.value = Settings(phoneTranscription = true)
        val model = viewModel()

        model.deleteModel()

        assertFalse(settings.state.value.phoneTranscription)
        assertNull(model.state.value.model)
    }

    @Test
    fun `battery settings are kept within range`() {
        val model = viewModel()

        model.setOnBattery(true)
        model.setBatteryPercent(5)

        assertTrue(settings.state.value.phoneTranscriptionOnBattery)
        assertEquals(20, settings.state.value.phoneTranscriptionBatteryPercent)
    }

    @Test
    fun `waiting audio is shown in seconds and drafts can be deleted`() {
        val model = viewModel()
        drafts.frames.value = 3_000
        drafts.texts.value =
            listOf(
                DraftTextRow(
                    session = "s",
                    firstSeq = 0,
                    startMs = 1,
                    endMs = 2,
                    text = "t",
                    model = "m",
                    createdAtMs = 3,
                ),
            )

        assertEquals(60L, model.state.value.waitingSeconds)
        assertEquals(1, model.state.value.drafts.size)

        model.deleteDrafts()

        assertTrue(
            model.state.value.drafts
                .isEmpty(),
        )
    }

    @Test
    fun `the benchmark shows its numbers, and a failure gets a sentence`() {
        val result =
            BenchmarkResult(
                model = "parakeet",
                loadMs = 4_000,
                audioMs = 60_000,
                workMs = 12_000,
                idleRssMb = 150,
                peakRssMb = 900,
                batteryPercentPerHour = 20.0,
                wordErrorRate = 0.05,
                text = "synthetic",
            )
        val good = viewModel(benchmark = FakeBenchmark(result))
        good.runBenchmark()
        assertEquals(
            0.2,
            good.state.value.benchmark!!
                .realTimeFactor,
            1e-9,
        )
        assertEquals(
            4.0,
            good.state.value.benchmark!!
                .batteryPercentPerSpeechHour!!,
            1e-9,
        )
        assertFalse(good.state.value.benchmarkRunning)

        val bad = viewModel()
        bad.runBenchmark()
        assertEquals(TranscriptionNotice.BenchmarkFailed, bad.state.value.notice)
    }
}
