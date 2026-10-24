package io.github.nytka_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Which tab bar the app shows: Today, Timeline and Over time once the server lists `today`, else the five tabs of
 * before. Null while the server has not answered; after [WAIT_MS] without an answer the five tabs show, and a late
 * answer that lists the feature switches once.
 */
@HiltViewModel
class LayoutViewModel
    @Inject
    constructor(
        private val info: InfoClient,
    ) : ViewModel() {
        private val mutableThreeTabs = MutableStateFlow<Boolean?>(null)
        val threeTabs: StateFlow<Boolean?> = mutableThreeTabs.asStateFlow()

        init {
            viewModelScope.launch {
                val answer = async { (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_TODAY) == true }
                val quick = withTimeoutOrNull(WAIT_MS) { answer.await() }
                mutableThreeTabs.value = quick ?: false
                if (quick == null && answer.await()) mutableThreeTabs.value = true
            }
        }

        companion object {
            const val WAIT_MS = 1_500L
        }
    }
