package io.github.nytka_app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.theme.NytkaTheme

/** Below this the battery meter turns to the error colour and says why. */
const val BATTERY_LOW_PERCENT = 10

/**
 * A titled group of settings, named for the place its settings live: Pendant, On this phone, Your server or
 * Developer. Never put a destructive action in one; those go on a detail screen behind a confirm dialog.
 */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** One setting or a few related ones inside a [SettingsCard], under a small heading. */
@Composable
fun SettingsBlock(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        content()
    }
}

/**
 * A label with an optional supporting line and one trailing element: a [value], a [trailing] control or, when
 * [onClick] is set and nothing trails, a chevron. [mono] sets the supporting line and value in the code style
 * (addresses, tokens, webhook URLs), which wraps anywhere.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    mono: Boolean = false,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(
                    it,
                    style = if (mono) NytkaTheme.typography.code else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        value?.let {
            Text(
                it,
                style = if (mono) NytkaTheme.typography.code else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            trailing != null -> trailing()
            onClick != null ->
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

/** A row whose trailing element is a [NytkaSwitch]; the whole row toggles it. */
@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    SettingsRow(
        label,
        modifier,
        supporting = supporting,
        onClick = { onCheckedChange(!checked) },
        trailing = { NytkaSwitch(checked = checked, onCheckedChange = null) },
    )
}

/**
 * The pendant's charge as a meter in a neutral colour. Only below [BATTERY_LOW_PERCENT] does it turn to the error
 * colour, and then a sentence says what to do, so the colour is never the whole message.
 */
@Composable
fun BatteryMeter(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val low = percent < BATTERY_LOW_PERCENT
    val description = stringResource(R.string.battery_percent_format, percent)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.battery), style = MaterialTheme.typography.bodyLarge)
            Text(
                "$percent%",
                style = MaterialTheme.typography.bodyLarge,
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (low) {
            Text(
                stringResource(R.string.battery_low_sentence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A secret shown as dots and its last four characters; the server's own hint is already those characters. */
fun maskedToken(hint: String): String = "••••••••" + hint.takeLast(MASK_VISIBLE)

private const val MASK_VISIBLE = 4
