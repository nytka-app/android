package io.github.nytka_app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Tag
import io.github.nytka_app.core.api.TagsClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What is typed in the sheet and the tags in use that start with it, most used first. */
data class TagSheetState(
    val query: String = "",
    val tags: List<Tag> = emptyList(),
)

/**
 * The suggestions of the add-tag sheet: `GET /tags?q=` for what is typed, 250 ms after the last key. A failure leaves
 * no suggestions; the field still works.
 */
@HiltViewModel
class TagSheetViewModel
    @Inject
    constructor(
        private val api: TagsClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TagSheetState())
        val state: StateFlow<TagSheetState> = mutableState.asStateFlow()
        private val typed = MutableStateFlow("")

        @OptIn(FlowPreview::class)
        private fun listAsTyped() =
            viewModelScope.launch {
                // The empty field lists at once; typing waits for a pause.
                typed.debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS }.collectLatest { q ->
                    val found = (api.tags(q) as? ApiResult.Ok)?.value.orEmpty()
                    mutableState.update { it.copy(tags = found) }
                }
            }

        init {
            listAsTyped()
        }

        fun setQuery(text: String) {
            mutableState.update { it.copy(query = text) }
            typed.value = text
        }

        companion object {
            const val DEBOUNCE_MS = 250L
        }
    }
