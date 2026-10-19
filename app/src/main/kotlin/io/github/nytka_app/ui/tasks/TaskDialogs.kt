package io.github.nytka_app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.names.NameCompletions
import io.github.nytka_app.ui.names.filled
import io.github.nytka_app.ui.names.knownPeople
import java.time.LocalDate

private const val MAX_TEXT = 200
private const val DAY_MILLIS = 86_400_000L

/** The add dialog: a task text and an optional due date. */
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (String, LocalDate?) -> Unit,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(filled("")) }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val people = knownPeople()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_task_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NytkaTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(MAX_TEXT)) },
                    label = stringResource(R.string.task_label),
                )
                NameCompletions(field, onValueChange = { field = it }, people = people)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { picking = true }) {
                        Text(
                            due?.let {
                                stringResource(
                                    R.string.task_due_format,
                                    Formatting.dayTitle(it, LocalDate.now()),
                                )
                            }
                                ?: stringResource(R.string.task_due_add),
                        )
                    }
                    if (due !=
                        null
                    ) {
                        TextButton(onClick = { due = null }) { Text(stringResource(R.string.task_due_clear)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(field.text, due) }, enabled = field.text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) {
        DuePicker(due, onDismiss = { picking = false }, onPick = {
            due = it
            picking = false
        })
    }
}

/** The date picker of one task's due date; a pick of "no date" clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuePicker(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate?) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDay() * DAY_MILLIS })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(
                        state.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MILLIS)) },
                    )
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (initial !=
                    null
                ) {
                    TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.task_due_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) { DatePicker(state) }
}
