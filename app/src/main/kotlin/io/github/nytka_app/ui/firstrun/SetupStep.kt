package io.github.nytka_app.ui.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.theme.NytkaTheme

private const val STEPS = 4

/**
 * The frame of a first-run step (design system: SetupStep): four progress segments, the step in words, a title, one
 * paragraph, the step's content, and the actions at the bottom. [step] counts from 0.
 */
@Composable
fun SetupStep(
    step: Int,
    title: String,
    body: String,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val label = stringArrayResource(R.array.first_run_step_labels)[step]
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(STEPS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clearAndSetSemantics {}
                        .background(
                            if (index <=
                                step
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            secondary()
            primary()
        }
    }
}

/** One permission: what it is, why Nytka needs it, and its state as a check and a word, not only a colour. */
@Composable
fun PermissionRow(
    name: String,
    why: String,
    granted: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.permission_allowed), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Text(
                    stringResource(R.string.permission_not_yet),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The consent step: four numbered points in Literata and the acknowledgement, never pre-ticked. Without an
 * [acknowledgement] it is the same text to read again (Settings › About recording), with no box.
 */
@Composable
fun ConsentNote(
    points: List<String>,
    acknowledgement: String? = null,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        points.forEachIndexed { index, point ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${index + 1}.",
                    style = NytkaTheme.typography.readingBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(point, style = NytkaTheme.typography.readingBody, modifier = Modifier.weight(1f))
            }
        }
        if (acknowledgement != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(acknowledgement, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
