package io.github.nytka_app.ui.developer.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.NytkaSwitch
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsNote
import io.github.nytka_app.ui.SettingsRow

/**
 * The server's settings, grouped by key prefix. Admin token only. Under Developer mode it lists every key; the
 * People tab limits it to the `people` prefix and adds a [footer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    title: String = stringResource(R.string.server_settings_title),
    footer: (@Composable () -> Unit)? = null,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.anyChanged && !state.saving) {
                        Text(
                            stringResource(R.string.action_save),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
            state.error?.let { error ->
                item {
                    if (state.fields.isEmpty()) {
                        ListFailure(error, offline = false, onRetry = viewModel::load)
                    } else {
                        ErrorLine(error)
                    }
                }
            }
            if (state.saved) item { SettingsNote(stringResource(R.string.saved_server_values)) }
            state.groups.forEach { (prefix, fields) ->
                item(key = "group-$prefix") {
                    SettingsCard(prefix) { fields.forEach { field -> SettingRow(field, viewModel::edit) } }
                }
            }
            footer?.let { item(key = "footer") { it() } }
        }
    }
}

@Composable
private fun SettingRow(
    field: SettingField,
    onEdit: (String, String) -> Unit,
) {
    val setting = field.setting
    val locked = setting.locked
    when {
        setting.secret ->
            SettingsRow(
                setting.key,
                supporting = stringResource(R.string.set_by_server_env),
                value = if (setting.isSet) stringResource(R.string.set) else stringResource(R.string.not_set),
            )

        setting.type == ServerSetting.TYPE_BOOL -> {
            val on = field.text.equals("true", ignoreCase = true)
            val notes =
                listOfNotNull(
                    stringResource(R.string.set_by_server_env).takeIf { locked },
                    stringResource(R.string.people_voice_matching_duty).takeIf { setting.key == VOICE_MATCHING_KEY },
                    stringResource(R.string.people_auto_apply_note).takeIf { setting.key == AUTO_APPLY_KEY },
                )
            Column {
                SettingsRow(
                    setting.key,
                    supporting = notes.joinToString(" ").ifEmpty { null },
                    onClick = if (locked) null else ({ onEdit(setting.key, (!on).toString()) }),
                    trailing = { NytkaSwitch(checked = on, onCheckedChange = null, enabled = !locked) },
                )
                if (field.errors.isNotEmpty()) ErrorLine(field.errors.joinToString(" "))
            }
        }

        else ->
            NytkaTextField(
                value = field.text,
                onValueChange = { onEdit(setting.key, it) },
                label = setting.key,
                mono = true,
                enabled = !locked,
                error = field.errors.takeIf { it.isNotEmpty() }?.joinToString(" "),
                supporting = fieldHint(setting),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardFor(setting.type)),
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Composable
private fun fieldHint(setting: ServerSetting): String? =
    when {
        setting.locked -> stringResource(R.string.set_by_server_env)
        !setting.default.isNullOrEmpty() -> stringResource(R.string.default_with_empty, setting.default ?: "")
        else -> null
    }

/** The setting whose switch carries the legal-duty sentence. */
private const val VOICE_MATCHING_KEY = "people.voiceMatching"

/** The setting whose switch says where auto-applied names show. */
private const val AUTO_APPLY_KEY = "people.autoApplyNames"

private fun keyboardFor(type: String): KeyboardType =
    when (type) {
        ServerSetting.TYPE_INT -> KeyboardType.Number
        ServerSetting.TYPE_NUMBER -> KeyboardType.Decimal
        "url" -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
