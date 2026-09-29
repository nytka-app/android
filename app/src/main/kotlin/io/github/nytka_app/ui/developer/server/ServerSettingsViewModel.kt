package io.github.nytka_app.ui.developer.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.core.api.forScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One setting as the screen edits it: [text] is what the field holds, [original] what the server had. */
data class SettingField(
    val setting: ServerSetting,
    val original: String,
    val text: String,
    val errors: List<String> = emptyList(),
) {
    val changed: Boolean get() = text != original
}

data class ServerSettingsUiState(
    val fields: List<SettingField> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** A failure that belongs to no field: a v0.1 server, a small token, a locked key. */
    val error: String? = null,
    val saved: Boolean = false,
) {
    val anyChanged: Boolean get() = fields.any { it.changed }

    /** The catalog grouped by key prefix (`llm`, `stt`, ...), in the server's order. */
    val groups: List<Pair<String, List<SettingField>>>
        get() = fields.groupBy { it.setting.key.substringBefore('.') }.toList()
}

@HiltViewModel
class ServerSettingsViewModel
    @Inject
    constructor(
        private val api: ServerSettingsClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ServerSettingsUiState())
        val state: StateFlow<ServerSettingsUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        fun load() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = api.settings()) {
                    is ApiResult.Ok ->
                        mutableState.value = ServerSettingsUiState(fields = result.value.map(::field), loading = false)
                    is ApiResult.Failure -> mutableState.update { it.copy(loading = false, error = result.forScreen()) }
                }
            }
        }

        fun edit(
            key: String,
            text: String,
        ) {
            mutableState.update { state ->
                state.copy(
                    fields =
                        state.fields.map {
                            if (it.setting.key == key &&
                                !it.setting.locked
                            ) {
                                it.copy(text = text, errors = emptyList())
                            } else {
                                it
                            }
                        },
                    saved = false,
                )
            }
        }

        /** Sends only the changed keys; an emptied field asks for the default back. */
        fun save() {
            val current = mutableState.value
            val changes =
                current.fields
                    .filter { it.changed }
                    .associate { it.setting.key to it.text.trim().ifEmpty { null } }
            if (changes.isEmpty() || current.saving) return
            mutableState.update { it.copy(saving = true, error = null, saved = false) }
            viewModelScope.launch {
                when (val result = api.update(changes)) {
                    is ApiResult.Ok ->
                        mutableState.value =
                            ServerSettingsUiState(fields = result.value.map(::field), loading = false, saved = true)
                    is ApiResult.Failure -> mutableState.update { it.failed(result) }
                }
            }
        }

        private fun ServerSettingsUiState.failed(result: ApiResult.Failure): ServerSettingsUiState {
            val known = fields.map { it.setting.key }.toSet()
            val leftover =
                result.errors
                    .filterKeys { it !in known }
                    .values
                    .flatten()
            return copy(
                saving = false,
                fields = fields.map { it.copy(errors = result.errors[it.setting.key].orEmpty()) },
                error =
                    when {
                        result.kind == FailureKind.Conflict ->
                            "The server refused a change: a setting is locked by its environment."
                        result.kind == FailureKind.Invalid && leftover.isEmpty() -> "Some values are not valid."
                        result.kind == FailureKind.Invalid -> leftover.joinToString(" ")
                        else -> result.forScreen()
                    },
            )
        }

        private fun field(setting: ServerSetting): SettingField {
            val text = setting.value ?: setting.default.orEmpty()
            return SettingField(setting, original = text, text = text)
        }
    }
