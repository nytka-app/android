package io.github.nytka_app.ui.transcribe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.core.transcribe.DraftTextRow
import io.github.nytka_app.core.transcribe.DraftsDao
import io.github.nytka_app.core.transcribe.TranscriberEngine
import io.github.nytka_app.transcribe.BenchmarkResult
import io.github.nytka_app.transcribe.InstalledModel
import io.github.nytka_app.transcribe.ModelImport
import io.github.nytka_app.transcribe.ModelLibrary
import io.github.nytka_app.transcribe.TranscriptionBenchmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What went wrong last; the screen words it, so no engine or system message reaches the user. */
enum class TranscriptionNotice { NoModelInFolder, NoRoom, ImportFailed, BenchmarkFailed }

data class PhoneTranscriptionUiState(
    /** False in the oss build and on phones the engine does not support. */
    val available: Boolean = false,
    val on: Boolean = false,
    val onBattery: Boolean = false,
    val batteryPercent: Int = 80,
    val model: InstalledModel? = null,
    /** A picked folder waiting for the user to confirm the copy. */
    val prompt: ModelImport? = null,
    /** Bytes copied so far while an import runs; null otherwise. */
    val importedBytes: Long? = null,
    val notice: TranscriptionNotice? = null,
    /** Audio copied from the pendant and not yet recognised. */
    val waitingSeconds: Long = 0,
    val drafts: List<DraftTextRow> = emptyList(),
    val developerMode: Boolean = false,
    val benchmarkRunning: Boolean = false,
    val benchmark: BenchmarkResult? = null,
)

@HiltViewModel
class PhoneTranscriptionViewModel
    @Inject
    constructor(
        private val settings: SettingsSource,
        private val models: ModelLibrary,
        private val drafts: DraftsDao,
        private val engine: TranscriberEngine,
        private val benchmark: TranscriptionBenchmark,
    ) : ViewModel() {
        private data class Local(
            val prompt: ModelImport? = null,
            val importedBytes: Long? = null,
            val notice: TranscriptionNotice? = null,
            val benchmarkRunning: Boolean = false,
            val benchmark: BenchmarkResult? = null,
        )

        private val local = MutableStateFlow(Local())

        val state: StateFlow<PhoneTranscriptionUiState> =
            combine(
                settings.settings,
                models.installed,
                drafts.audioFrames(),
                drafts.newestTexts(SHOWN_DRAFTS),
                local,
            ) { current, model, frames, texts, local ->
                PhoneTranscriptionUiState(
                    available = engine.available,
                    on = current.phoneTranscription,
                    onBattery = current.phoneTranscriptionOnBattery,
                    batteryPercent = current.phoneTranscriptionBatteryPercent,
                    model = model,
                    prompt = local.prompt,
                    importedBytes = local.importedBytes,
                    notice = local.notice,
                    waitingSeconds = frames * ChunkFormat.FRAME_MS / 1000,
                    drafts = texts,
                    developerMode = current.developerMode,
                    benchmarkRunning = local.benchmarkRunning,
                    benchmark = local.benchmark,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, PhoneTranscriptionUiState())

        /** Switching on needs a model; the screen offers the switch only then. */
        fun setOn(on: Boolean) {
            if (on && models.installed.value == null) return
            viewModelScope.launch { settings.update { it.copy(phoneTranscription = on) } }
        }

        fun setOnBattery(on: Boolean) {
            viewModelScope.launch { settings.update { it.copy(phoneTranscriptionOnBattery = on) } }
        }

        fun setBatteryPercent(percent: Int) {
            val clamped = percent.coerceIn(MIN_BATTERY, MAX_BATTERY)
            viewModelScope.launch { settings.update { it.copy(phoneTranscriptionBatteryPercent = clamped) } }
        }

        /** Reads the folder the picker returned and asks before copying; nothing is copied yet. */
        @Suppress("TooGenericExceptionCaught") // a provider can fail in any way; the user gets one sentence
        fun folderPicked(folder: String) {
            viewModelScope.launch {
                try {
                    val import = models.inspect(folder)
                    local.update { it.copy(prompt = import, notice = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    local.update { it.copy(notice = TranscriptionNotice.NoModelInFolder) }
                }
            }
        }

        fun dismissImport() = local.update { it.copy(prompt = null) }

        @Suppress("TooGenericExceptionCaught")
        fun confirmImport() {
            val import = local.value.prompt ?: return
            if (!import.fits) {
                local.update { it.copy(prompt = null, notice = TranscriptionNotice.NoRoom) }
                return
            }
            local.update { it.copy(prompt = null, importedBytes = 0, notice = null) }
            viewModelScope.launch {
                try {
                    models.install(import) { copied -> local.update { it.copy(importedBytes = copied) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    local.update { it.copy(notice = TranscriptionNotice.ImportFailed) }
                } finally {
                    local.update { it.copy(importedBytes = null) }
                }
            }
        }

        /** Also switches recognition off: without a model it cannot run. */
        fun deleteModel() {
            viewModelScope.launch {
                settings.update { it.copy(phoneTranscription = false) }
                models.delete()
                local.update { it.copy(benchmark = null) }
            }
        }

        fun deleteDrafts() {
            viewModelScope.launch { drafts.deleteAllTexts() }
        }

        @Suppress("TooGenericExceptionCaught")
        fun runBenchmark() {
            if (local.value.benchmarkRunning) return
            local.update { it.copy(benchmarkRunning = true, notice = null) }
            viewModelScope.launch {
                try {
                    val result = benchmark.run()
                    local.update { it.copy(benchmark = result) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    local.update { it.copy(notice = TranscriptionNotice.BenchmarkFailed) }
                } finally {
                    local.update { it.copy(benchmarkRunning = false) }
                }
            }
        }

        fun dismissNotice() = local.update { it.copy(notice = null) }

        private companion object {
            const val SHOWN_DRAFTS = 50
            const val MIN_BATTERY = 20
            const val MAX_BATTERY = 100
        }
    }
