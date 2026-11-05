package io.github.nytka_app.ui

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
import io.github.nytka_app.pendant.RecorderModel

/**
 * Which recorder to pair. [selected] starts as Omi and only the user changes it; Limitless says it has
 * not been verified on real hardware once it is picked, not before.
 */
@Composable
fun RecorderChoice(
    selected: RecorderModel,
    onSelect: (RecorderModel) -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RecorderModel.entries.forEach { model ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(model == selected, role = Role.RadioButton, onClick = { onSelect(model) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(model == selected, onClick = null)
                Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (selected == RecorderModel.Limitless) {
            Text(stringResource(R.string.limitless_unverified), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.limitless_unverified_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
