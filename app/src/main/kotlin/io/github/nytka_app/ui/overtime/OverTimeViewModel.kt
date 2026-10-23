package io.github.nytka_app.ui.overtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Insight
import io.github.nytka_app.core.api.Insights
import io.github.nytka_app.core.api.InsightsClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.NEEDS_UPDATE
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.unreachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverTimeUiState(
    val insights: Insights? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
) {
    /** Unlocked first, then the locked ones nearest to unlocking. */
    val ordered: List<Insight>
        get() =
            insights
                ?.insights
                .orEmpty()
                .sortedWith(
                    compareBy<Insight> { !it.unlocked }.thenBy {
                        it.progress?.let { p ->
                            p.need - p.have
                        } ?: 0
                    },
                )
}

/**
 * Over time: patterns and reports. A locked insight shows how much more it needs ("needs 6 more days"), never an
 * empty chart. A server without `insights` gets "This server needs an update".
 */
@HiltViewModel
class OverTimeViewModel
    @Inject
    constructor(
        private val api: InsightsClient,
        private val info: InfoClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(OverTimeUiState())
        val state: StateFlow<OverTimeUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val server = info.info()
                if (server is ApiResult.Ok && !server.value.has(ServerInfo.FEATURE_INSIGHTS)) {
                    mutableState.update { it.copy(loading = false, error = NEEDS_UPDATE, offline = false) }
                    return@launch
                }
                when (val result = api.insights()) {
                    is ApiResult.Ok -> mutableState.value = OverTimeUiState(result.value, loading = false)
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                offline = result.kind.unreachable,
                                error = result.notice().takeUnless { result.kind.unreachable },
                            )
                        }
                }
            }
        }
    }
