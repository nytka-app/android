package io.github.nytka_app.ui.developer.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.AccessToken
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.CreatedToken
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.TokensClient
import io.github.nytka_app.core.api.forScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TokensUiState(
    val tokens: List<AccessToken> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** Why the last Create was refused, shown in its dialog. */
    val createError: String? = null,
    /** The token just made: shown once with Copy, then gone for good. */
    val created: CreatedToken? = null,
)

@HiltViewModel
class TokensViewModel
    @Inject
    constructor(
        private val api: TokensClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TokensUiState())
        val state: StateFlow<TokensUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        fun load() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = api.tokens()) {
                    is ApiResult.Ok -> mutableState.update { it.copy(tokens = result.value, loading = false) }
                    is ApiResult.Failure -> mutableState.update { it.copy(loading = false, error = result.forScreen()) }
                }
            }
        }

        fun create(
            name: String,
            scope: String,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME) {
                return mutableState.update { it.copy(createError = "Use 1 to $MAX_NAME characters for the name.") }
            }
            mutableState.update { it.copy(createError = null) }
            viewModelScope.launch {
                when (val result = api.createToken(trimmed, scope)) {
                    is ApiResult.Ok -> {
                        mutableState.update { it.copy(created = result.value) }
                        load()
                    }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                createError =
                                    when (result.kind) {
                                        FailureKind.Conflict -> "A token with this name is in use."
                                        FailureKind.Invalid ->
                                            result.errors.values
                                                .flatten()
                                                .joinToString(
                                                    " ",
                                                ).ifEmpty { result.message }
                                        else -> result.forScreen()
                                    },
                            )
                        }
                }
            }
        }

        fun clearCreateError() = mutableState.update { it.copy(createError = null) }

        /** The secret is dropped with the dialog; the server cannot show it again. */
        fun dismissCreated() = mutableState.update { it.copy(created = null) }

        fun revoke(id: String) {
            viewModelScope.launch {
                when (val result = api.revokeToken(id)) {
                    is ApiResult.Ok -> load()
                    is ApiResult.Failure -> mutableState.update { it.copy(error = result.forScreen()) }
                }
            }
        }

        private companion object {
            const val MAX_NAME = 64
        }
    }
