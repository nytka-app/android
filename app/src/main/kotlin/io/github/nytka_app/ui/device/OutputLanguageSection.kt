package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsNote

/** The Device tab's picker for `llm.outputLanguage`: summaries in English, Ukrainian or the conversation's language. */
@Composable
internal fun OutputLanguageSection(
    language: String,
    locked: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
) {
    SettingsBlock(stringResource(R.string.output_language_title)) {
        Column(Modifier.selectableGroup()) {
            OutputLanguageChoice(
                DeviceViewModel.OUTPUT_LANGUAGE_AUTO,
                stringResource(R.string.output_language_auto),
                language,
                locked,
                onSelect,
            )
            OutputLanguageChoice(
                DeviceViewModel.OUTPUT_LANGUAGE_ENGLISH,
                stringResource(R.string.output_language_english),
                language,
                locked,
                onSelect,
            )
            OutputLanguageChoice(
                DeviceViewModel.OUTPUT_LANGUAGE_UKRAINIAN,
                stringResource(R.string.output_language_ukrainian),
                language,
                locked,
                onSelect,
            )
        }
        if (locked) SettingsNote(stringResource(R.string.set_by_server_env))
        error?.let { ErrorLine(it) }
    }
}

@Composable
private fun OutputLanguageChoice(
    value: String,
    label: String,
    selected: String,
    locked: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected == value, enabled = !locked, role = Role.RadioButton, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected == value, onClick = null, enabled = !locked)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
