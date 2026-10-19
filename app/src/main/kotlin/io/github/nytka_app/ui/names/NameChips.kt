package io.github.nytka_app.ui.names

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.Monogram

/** The named people of the server, for the fields of the screen this is called from. */
@Composable
fun knownPeople(viewModel: KnownPeopleViewModel = hiltViewModel()): List<Person> {
    val people by viewModel.people.collectAsStateWithLifecycle()
    return people
}

/** A row of names, each with its monogram; a tap calls [onPick]. Nothing shows for no names. */
@Composable
fun NameChips(
    names: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (names.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            AssistChip(
                onClick = { onPick(name) },
                enabled = enabled,
                label = { Text(name) },
                leadingIcon = { Monogram(name, size = 24.dp) },
            )
        }
    }
}

/**
 * Names that finish the word before the cursor of a free-text field, from [people]; a tap writes the name in. Prose
 * asks only on a capital letter; a search field passes [capitalOnly] false.
 */
@Composable
fun NameCompletions(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    people: List<Person>,
    modifier: Modifier = Modifier,
    capitalOnly: Boolean = true,
) {
    NameChips(completions(people, value, capitalOnly), onPick = { onValueChange(complete(value, it)) }, modifier)
}

/**
 * The value of a field whose text a view model holds: the cursor is kept while typing, and when the view model changes
 * the text itself (a clear button, a cut to the limit) the field follows with the cursor at the end.
 */
@Composable
fun rememberFieldValue(text: String): MutableState<TextFieldValue> {
    val field = remember { mutableStateOf(filled(text)) }
    LaunchedEffect(text) { if (field.value.text != text) field.value = filled(text) }
    return field
}
