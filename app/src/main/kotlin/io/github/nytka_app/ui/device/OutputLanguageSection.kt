package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** The Device tab's picker for `llm.outputLanguage`: titles and summaries in English, Ukrainian, or whatever the conversation was spoken in. */
@Composable
internal fun OutputLanguageSection(
    language: String,
    locked: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
) {
    Section(stringResource(R.string.output_language_title)) {
        Column {
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
        if (locked) {
            Text(stringResource(R.string.set_by_server_env), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected == value, onClick = { onSelect(value) }, enabled = !locked)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
