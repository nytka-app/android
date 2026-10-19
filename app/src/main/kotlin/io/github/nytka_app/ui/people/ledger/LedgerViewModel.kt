package io.github.nytka_app.ui.people.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Ledger
import io.github.nytka_app.core.api.LedgerClient
import io.github.nytka_app.core.api.LedgerSide
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.tasks.taskSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** One promise: [source] is the conversation's title and day; [conversationId] is null for a task added by hand. */
data class LedgerEntry(
    val id: String,
    val text: String,
    val source: String,
    val conversationId: String?,
)

data class LedgerSideState(
    val open: List<LedgerEntry> = emptyList(),
    val done: List<LedgerEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = open.isEmpty() && done.isEmpty()
}

data class LedgerUiState(
    /** `/info` lists `ledger`; without it the section does not exist. */
    val available: Boolean = false,
    val owedByMe: LedgerSideState = LedgerSideState(),
    val owedToMe: LedgerSideState = LedgerSideState(),
    val loading: Boolean = false,
    /** Why the read failed, except a server that lacks the ledger, which shows nothing. */
    val error: String? = null,
)

/** The promises between you and the person whose page is open: what you owe them and what they owe you. */
@HiltViewModel
class LedgerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: LedgerClient,
        private val info: InfoClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"]) { "The person route carries an id." }
        private val mutableState = MutableStateFlow(LedgerUiState())
        val state: StateFlow<LedgerUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        /** Reads `/info` first: a server without `ledger` is never asked for one. */
        fun refresh() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val on = (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_LEDGER) == true
                if (!on) return@launch hide(error = null)
                when (val result = api.ledger(id)) {
                    is ApiResult.Ok -> show(result.value)
                    is ApiResult.Failure ->
                        hide(
                            error =
                                result.notice().takeUnless {
                                    result.kind == FailureKind.NotFound || result.kind == FailureKind.Unsupported
                                },
                        )
                }
            }
        }

        private fun hide(error: String?) =
            mutableState.update { LedgerUiState(available = error != null, loading = false, error = error) }

        private fun show(ledger: Ledger) =
            mutableState.update {
                LedgerUiState(
                    available = true,
                    owedByMe = side(ledger.owedByMe),
                    owedToMe = side(ledger.owedToMe),
                )
            }

        private fun side(side: LedgerSide) =
            LedgerSideState(
                open = side.open.map { entry(it) },
                done = side.done.map { entry(it) },
            )

        private fun entry(task: NytkaTask) =
            LedgerEntry(task.id, task.text, taskSource(task, clock), task.conversationId)
    }
