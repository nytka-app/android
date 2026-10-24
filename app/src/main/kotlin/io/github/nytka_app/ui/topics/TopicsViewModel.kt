package io.github.nytka_app.ui.topics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Topic
import io.github.nytka_app.core.api.TopicPage
import io.github.nytka_app.core.api.TopicsClient
import io.github.nytka_app.ui.itemNotice
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.unreachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

data class TopicsUiState(
    val all: List<Topic> = emptyList(),
    /** null shows both kinds; else [Topic.KIND_TAG] or [Topic.KIND_PERSON]. */
    val kind: String? = null,
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
) {
    /** Loaded, reachable, and no topic to show: the screen says how topics appear. */
    val nothing: Boolean get() = !loading && error == null && !offline && shown.isEmpty()

    /** The topics of [kind] whose name holds [query], most recently heard first. */
    val shown: List<Topic>
        get() =
            all
                .filter { kind == null || it.kind == kind }
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                .sortedByDescending { it.lastAt.orEmpty() }
}

/** Topic pages: every tag and person the server keeps a page for, filtered on the phone by kind and name. */
@HiltViewModel
class TopicsViewModel
    @Inject
    constructor(
        private val api: TopicsClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TopicsUiState())
        val state: StateFlow<TopicsUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                when (val result = api.topics()) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                all = result.value,
                                loading = false,
                                error = null,
                                offline = false,
                            )
                        }
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

        fun setKind(kind: String?) = mutableState.update { it.copy(kind = kind) }

        fun setQuery(query: String) = mutableState.update { it.copy(query = query) }
    }

data class TopicUiState(
    val page: TopicPage? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/** One topic page (`topic/{id}`): its summary, open commitments and moments, newest first, paged on demand. */
@HiltViewModel
class TopicViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: TopicsClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"]) { "The topic route carries an id." }
        private val mutableState = MutableStateFlow(TopicUiState())
        val state: StateFlow<TopicUiState> = mutableState.asStateFlow()
        val zone: ZoneId get() = clock.zone

        init {
            viewModelScope.launch {
                when (val result = api.topic(id)) {
                    is ApiResult.Ok -> mutableState.value = TopicUiState(result.value, loading = false)
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                error = result.itemNotice(),
                            )
                        }
                }
            }
        }

        /** Reads the next moments, once at a time; a failure leaves the page as it is. */
        fun loadMore() {
            val current = mutableState.value
            val before = current.page?.nextBefore ?: return
            if (current.loadingMore) return
            mutableState.update { it.copy(loadingMore = true) }
            viewModelScope.launch {
                val next = (api.topic(id, before) as? ApiResult.Ok)?.value
                mutableState.update { state ->
                    val page = state.page
                    if (next == null || page == null) {
                        state.copy(loadingMore = false)
                    } else {
                        state.copy(
                            page = page.copy(timeline = page.timeline + next.timeline, nextBefore = next.nextBefore),
                            loadingMore = false,
                        )
                    }
                }
            }
        }
    }
