package io.github.nytka_app.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.RelatedClient
import io.github.nytka_app.core.api.RelatedMoment
import io.github.nytka_app.core.api.ServerInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Earlier" strip of one thing: earlier moments about the same thing (`related`). Moments the thing already
 * carries are shown as they are; otherwise they are read once from `GET /related`. A server without `related`, or any
 * failure, shows no strip: it is a hint, never a state of its own.
 */
@HiltViewModel
class RelatedViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: RelatedClient,
        private val info: InfoClient,
    ) : ViewModel() {
        /** The conversation of the screen this strip sits on, when its route carries one. */
        val routeConversation: String? = savedState["id"]
        private val mutableMoments = MutableStateFlow<List<RelatedMoment>>(emptyList())
        val moments: StateFlow<List<RelatedMoment>> = mutableMoments.asStateFlow()
        private var asked: Pair<String, String>? = null
        private var job: Job? = null

        /** Shows the earlier moments of [id], a thing of [kind] ("conversation", "task", "memory", "note"). */
        fun load(
            kind: String,
            id: String,
            known: List<RelatedMoment> = emptyList(),
        ) {
            if (asked == kind to id) return
            asked = kind to id
            job?.cancel()
            mutableMoments.value = known
            if (known.isNotEmpty()) return
            job =
                viewModelScope.launch {
                    if ((info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_RELATED) != true) return@launch
                    (api.related(kind, id) as? ApiResult.Ok)?.let { mutableMoments.value = it.value }
                }
        }

        companion object {
            const val KIND_CONVERSATION = "conversation"
        }
    }
