package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.settings.MuteSchedule
import io.github.nytka_app.core.settings.MuteWindow
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsNote
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/** The weekly windows in which nothing is recorded. Edits save at once. */
@Composable
fun MuteScheduleSection(
    schedule: MuteSchedule,
    onChange: (MuteSchedule) -> Unit,
    server: MuteServerState? = null,
    timeZoneHint: String? = null,
    timeZoneError: String? = null,
    onSetServerTimeZone: () -> Unit = {},
) {
    SettingsBlock(stringResource(R.string.mute_schedule)) {
        SettingsNote(stringResource(R.string.mute_schedule_description))
        schedule.windows.forEachIndexed { index, window ->
            WindowEditor(
                window,
                onChange = { changed ->
                    onChange(
                        schedule.copy(
                            windows =
                                schedule.windows.toMutableList().also {
                                    it[index] =
                                        changed
                                },
                        ),
                    )
                },
                onRemove = { onChange(schedule.copy(windows = schedule.windows.filterIndexed { i, _ -> i != index })) },
            )
        }
        FilledTonalButton(onClick = { onChange(schedule.copy(windows = schedule.windows + NEW_WINDOW)) }) {
            Text(stringResource(R.string.add_window))
        }
        when (server) {
            null -> Unit
            MuteServerState.Applied -> SettingsNote(stringResource(R.string.mute_applied_on_server))
            is MuteServerState.Failed -> ErrorLine(stringResource(R.string.mute_server_failed_format, server.reason))
        }
        timeZoneHint?.let { zone ->
            SettingsNote(stringResource(R.string.time_zone_hint_format, zone))
            OutlinedButton(onClick = onSetServerTimeZone) {
                Text(stringResource(R.string.set_server_time_zone_format, zone))
            }
        }
        timeZoneError?.let { ErrorLine(it) }
    }
}

private val NEW_WINDOW = MuteWindow(DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(7, 0))

@Composable
private fun WindowEditor(
    window: MuteWindow,
    onChange: (MuteWindow) -> Unit,
    onRemove: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in window.days,
                    onClick = {
                        onChange(
                            window.copy(
                                days =
                                    if (day in
                                        window.days
                                    ) {
                                        window.days - day
                                    } else {
                                        window.days + day
                                    },
                            ),
                        )
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) {
                Text(stringResource(R.string.window_from_format, window.start.toString()))
            }
            OutlinedButton(onClick = { picking = false }) {
                Text(stringResource(R.string.window_to_format, window.end.toString()))
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
        if (!window.active) {
            SettingsNote(stringResource(R.string.window_inactive_hint))
        }
    }
    picking?.let { start ->
        TimeDialog(
            initial = if (start) window.start else window.end,
            onDismiss = { picking = null },
            onPicked = { time ->
                picking = null
                onChange(if (start) window.copy(start = time) else window.copy(end = time))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
