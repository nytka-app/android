package io.github.nytka_app.ui.developer.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.core.api.ServerSetting

/** The server's settings, grouped by key prefix. Developer mode, admin token only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.anyChanged && !state.saving) { Text("Save") }
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
            if (state.loading) item { CircularProgressIndicator() }
            state.error?.let { error ->
                item {
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        if (state.fields.isEmpty()) TextButton(onClick = viewModel::load) { Text("Retry") }
                    }
                }
            }
            if (state.saved) item { Text("Saved. The server uses the new values from its next job.") }
            state.groups.forEach { (prefix, fields) ->
                item(key = "group-$prefix") { Text(prefix, style = MaterialTheme.typography.titleMedium) }
                fields.forEach { field ->
                    item(key = field.setting.key) { SettingRow(field, viewModel::edit) }
                }
            }
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(setting.key)
                    Text("Set by the server's environment", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (setting.isSet) "Set" else "Not set")
            }

        setting.type == ServerSetting.TYPE_BOOL ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(setting.key)
                    if (locked) Text("Set by the server's environment", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = field.text.equals("true", ignoreCase = true),
                    onCheckedChange = { onEdit(setting.key, it.toString()) },
                    enabled = !locked,
                )
            }

        else ->
            OutlinedTextField(
                value = field.text,
                onValueChange = { onEdit(setting.key, it) },
                label = { Text(setting.key) },
                enabled = !locked,
                singleLine = true,
                isError = field.errors.isNotEmpty(),
                supportingText = { SettingHint(field) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardFor(setting.type)),
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Composable
private fun SettingHint(field: SettingField) {
    val setting = field.setting
    when {
        field.errors.isNotEmpty() -> Text(field.errors.joinToString(" "))
        setting.locked -> Text("Set by the server's environment")
        !setting.default.isNullOrEmpty() -> Text("Default: ${setting.default}. Empty restores it.")
    }
}

private fun keyboardFor(type: String): KeyboardType =
    when (type) {
        ServerSetting.TYPE_INT -> KeyboardType.Number
        "url" -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
