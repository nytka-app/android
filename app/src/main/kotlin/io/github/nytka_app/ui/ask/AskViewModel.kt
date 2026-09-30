package io.github.nytka_app.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AskAnswer
import io.github.nytka_app.core.api.AskClient
import io.github.nytka_app.core.api.AskSource
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.memories.MemoryFormatting
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** [title] is null for a memory, whose text is the snippet. [openId] is the conversation a tap opens, if any. */
data class AskSourceRow(
    val n: Int,
    val title: String?,
    val snippet: String,
    val date: String,
    val openId: String?,
)

data class AskResult(
    val parts: List<AnswerPart>,
    val sources: List<AskSourceRow>,
)

data class AskUiState(
    val question: String = "",
    val asking: Boolean = false,
    val result: AskResult? = null,
    val error: String? = null,
) {
    val canAsk: Boolean get() = !asking && question.isNotBlank()
}

@HiltViewModel
class AskViewModel
    @Inject
    constructor(
        private val api: AskClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AskUiState())
        val state: StateFlow<AskUiState> = mutableState.asStateFlow()

        fun setQuestion(question: String) = mutableState.update { it.copy(question = question.take(MAX_QUESTION)) }

        fun ask() {
            val current = mutableState.value
            if (!current.canAsk) return
            mutableState.update { it.copy(asking = true, error = null) }
            viewModelScope.launch {
                when (val result = api.ask(current.question.trim())) {
                    is ApiResult.Ok -> mutableState.update { it.copy(asking = false, result = result(result.value)) }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                asking = false,
                                error =
                                    when (result.kind) {
                                        FailureKind.Unavailable -> NO_MODEL
                                        FailureKind.Timeout -> TIMED_OUT
                                        FailureKind.Network -> UNREACHABLE
                                        else -> result.notice()
                                    },
                            )
                        }
                }
            }
        }

        private fun result(answer: AskAnswer): AskResult {
            val rows = answer.sources.map(::row)
            return AskResult(AnswerParts.parse(answer.answer, rows.associate { it.n to it.openId }), rows)
        }

        private fun row(source: AskSource) =
            AskSourceRow(
                n = source.n,
                title = source.title,
                snippet = AnswerParts.plain(source.snippet),
                date = MemoryFormatting.day(source.at, clock).orEmpty(),
                openId = source.conversationId ?: source.id.takeIf { source.kind == "conversation" },
            )

        companion object {
            const val MAX_QUESTION = 500
            const val NO_MODEL = "The server has no model set up to answer questions."
            const val TIMED_OUT = "The server took too long to answer. Try a shorter question."
            const val UNREACHABLE = "The server could not be reached."
        }
    }
