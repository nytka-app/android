package io.github.nytka_app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.VoiceprintsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What deleting Nytka's voice models came to; the screen words it, never the server's own text. */
sealed interface VoiceModelsNotice {
    data object Deleted : VoiceModelsNotice

    data class Failed(
        val kind: FailureKind,
        val message: String,
    ) : VoiceModelsNotice
}

data class VoiceModelsUiState(
    val confirming: Boolean = false,
    val deleting: Boolean = false,
    val notice: VoiceModelsNotice? = null,
)

/** "Delete all voice models" in People settings: Nytka's own groups and voiceprints, asked about first. */
@HiltViewModel
class VoiceModelsViewModel
    @Inject
    constructor(
        private val voiceprints: VoiceprintsClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(VoiceModelsUiState())
        val state: StateFlow<VoiceModelsUiState> = mutableState.asStateFlow()

        fun ask() = mutableState.update { it.copy(confirming = true, notice = null) }

        fun cancel() = mutableState.update { it.copy(confirming = false) }

        fun noticeShown() = mutableState.update { it.copy(notice = null) }

        /** Called only from the confirm button. */
        fun confirm() {
            if (mutableState.value.deleting) return
            mutableState.update { it.copy(confirming = false, deleting = true) }
            viewModelScope.launch {
                val notice =
                    when (val result = voiceprints.deleteAll()) {
                        is ApiResult.Ok -> VoiceModelsNotice.Deleted
                        is ApiResult.Failure -> VoiceModelsNotice.Failed(result.kind, result.message)
                    }
                mutableState.update { it.copy(deleting = false, notice = notice) }
            }
        }
    }
