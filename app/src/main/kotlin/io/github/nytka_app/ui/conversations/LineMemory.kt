package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.memories.MemoriesViewModel
import io.github.nytka_app.ui.names.NameCompletions
import io.github.nytka_app.ui.names.filled
import io.github.nytka_app.ui.names.knownPeople
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "Save as memory" dialog shows: the memory being saved, or why it was not. */
data class LineMemoryState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * Saves one transcript line as a memory: `POST /memories` with the text the person confirmed. The server answers 409
 * for a fact it already holds; that counts as saved.
 */
@HiltViewModel
class LineMemoryViewModel
    @Inject
    constructor(
        private val api: MemoriesClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LineMemoryState())
        val state: StateFlow<LineMemoryState> = mutableState.asStateFlow()

        fun save(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length > MemoriesViewModel.MAX_LENGTH || mutableState.value.saving) return
            mutableState.value = LineMemoryState(saving = true)
            viewModelScope.launch {
                mutableState.value =
                    when (val result = api.addMemory(trimmed)) {
                        is ApiResult.Ok -> LineMemoryState(saved = true)
                        is ApiResult.Failure ->
                            LineMemoryState(
                                error = if (result.kind == FailureKind.Conflict) null else result.notice(),
                                saved = result.kind == FailureKind.Conflict,
                            )
                    }
            }
        }

        fun reset() = mutableState.update { LineMemoryState() }

        companion object {
            /** "Olena: I will send the contract", cut to the memory limit; a line of the wearer carries no name. */
            fun draft(
                speaker: String?,
                text: String,
            ): String {
                val line = if (speaker.isNullOrBlank()) text else "$speaker: $text"
                return line.take(MemoriesViewModel.MAX_LENGTH)
            }
        }
    }

/** The text offered for [paragraph]: the line, led by the speaker unless that is the wearer or an unnamed voice. */
fun lineMemoryDraft(paragraph: Paragraph): String =
    LineMemoryViewModel.draft(
        paragraph.speaker.takeUnless { paragraph.isUser == true || paragraph.unknownVoice },
        paragraph.text,
    )

/** The dialog for [draft] while it is not null, and a note on [snackbar] once the server holds the memory. */
@Composable
fun LineMemoryHost(
    draft: String?,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.line_memory_saved)
    draft?.let {
        LineMemoryDialog(
            draft = it,
            onDismiss = onClose,
            onDone = {
                onClose()
                scope.launch { snackbar.showSnackbar(saved) }
            },
        )
    }
}

/** Saves a line as a memory, after the text has been read and edited; [onDone] runs once the server holds it. */
@Composable
fun LineMemoryDialog(
    draft: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    viewModel: LineMemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(filled(draft)) }
    val people = knownPeople()
    if (state.saved) {
        viewModel.reset()
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.line_memory_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(MemoriesViewModel.MAX_LENGTH)) },
                    label = { Text(stringResource(R.string.memory_label)) },
                    supportingText = {
                        state.error?.let { ErrorLine(it) }
                            ?: Text("${field.text.length} / ${MemoriesViewModel.MAX_LENGTH}")
                    },
                    shape = MaterialTheme.shapes.small,
                    isError = state.error != null,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                NameCompletions(field, onValueChange = { field = it }, people = people)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.save(field.text) },
                enabled = field.text.isNotBlank() && !state.saving,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
