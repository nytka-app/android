package io.github.nytka_app.ui.overtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Rollup
import io.github.nytka_app.core.api.RollupsClient
import io.github.nytka_app.core.api.ServerInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RollupsUiState(
    /** False on a server without `rollups`: the section does not show. */
    val available: Boolean = false,
    /** True once the server said whether it has `rollups`. */
    val checked: Boolean = false,
    val period: String = Rollup.WEEK,
    /** The summary shown; null while loading or when the period has none yet. */
    val rollup: Rollup? = null,
    val loading: Boolean = false,
    /** True when a later summary of the period was shown before this one. */
    val canGoLater: Boolean = false,
    /** False once the server had nothing before [rollup]. */
    val canGoEarlier: Boolean = true,
)

/**
 * Summaries over time in Over time: the newest week, month, year or all time, and earlier ones one at a time. A
 * locked summary shows what it still needs, as a locked insight does.
 */
@HiltViewModel
class RollupsViewModel
    @Inject
    constructor(
        private val api: RollupsClient,
        private val info: InfoClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(RollupsUiState())
        val state: StateFlow<RollupsUiState> = mutableState.asStateFlow()

        /** The later summaries passed on the way back, newest last, for [later]. */
        private val later = ArrayDeque<Rollup>()
        private var job: Job? = null

        init {
            viewModelScope.launch {
                val on = (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_ROLLUPS) == true
                mutableState.update { it.copy(available = on, checked = true) }
                if (!on) return@launch
                show(Rollup.WEEK)
            }
        }

        fun show(period: String) {
            later.clear()
            mutableState.update { it.copy(period = period, rollup = null, canGoLater = false, canGoEarlier = true) }
            load(period, before = null)
        }

        fun earlier() {
            val current = mutableState.value
            val rollup = current.rollup ?: return
            if (!current.canGoEarlier || current.loading) return
            later.addLast(rollup)
            load(current.period, before = rollup.start)
        }

        fun later() {
            val rollup = later.removeLastOrNull() ?: return
            mutableState.update { it.copy(rollup = rollup, canGoLater = later.isNotEmpty(), canGoEarlier = true) }
        }

        private fun load(
            period: String,
            before: String?,
        ) {
            job?.cancel()
            mutableState.update { it.copy(loading = true) }
            job =
                viewModelScope.launch {
                    val found = (api.rollups(period, before, limit = 1) as? ApiResult.Ok)?.value?.firstOrNull()
                    // Nothing earlier: stay on the summary that was shown.
                    val shown = found ?: if (before != null) later.removeLastOrNull() else null
                    val more = found != null || before == null
                    val hasLater = later.isNotEmpty()
                    mutableState.update {
                        it.copy(rollup = shown, loading = false, canGoEarlier = more, canGoLater = hasLater)
                    }
                }
        }
    }
