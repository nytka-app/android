package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.FailureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class Paragraph(
    val time: String,
    val text: String,
)

data class ConversationUiState(
    val title: String = "",
    val timeRange: String = "",
    val length: String = "",
    val paragraphs: List<Paragraph> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
    val raw: String? = null,
)

@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: ConversationsClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"]) { "The conversation route carries an id." }
        private val mutableState = MutableStateFlow(ConversationUiState())
        val state: StateFlow<ConversationUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                when (val result = api.conversation(id)) {
                    is ApiResult.Ok -> {
                        val zone = clock.zone
                        val start = Instant.parse(result.value.startedAt)
                        val end = Instant.parse(result.value.endedAt)
                        mutableState.value =
                            ConversationUiState(
                                title = Formatting.dayTitle(start.atZone(zone).toLocalDate(), LocalDate.now(clock)),
                                timeRange = Formatting.timeRange(start, end, zone),
                                length = Formatting.length(start, end),
                                paragraphs =
                                    result.value.segments.map {
                                        Paragraph(
                                            Formatting.clock(Instant.parse(it.startedAt), zone),
                                            it.text,
                                        )
                                    },
                                loading = false,
                            )
                    }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                error =
                                    if (result.kind ==
                                        FailureKind.NotFound
                                    ) {
                                        "This conversation no longer exists."
                                    } else {
                                        result.message
                                    },
                            )
                        }
                }
            }
        }

        fun delete() {
            viewModelScope.launch {
                when (val result = api.deleteConversation(id)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(deleted = true) }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.message) }
                }
            }
        }

        fun loadRaw() {
            viewModelScope.launch {
                when (val result = api.transcriptionsJson(id)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(raw = result.value) }
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.message) }
                }
            }
        }
    }
