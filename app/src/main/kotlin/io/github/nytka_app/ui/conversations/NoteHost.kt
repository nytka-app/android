package io.github.nytka_app.ui.conversations

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** A snackbar host that shows [note] once, then calls [shown]. */
@Composable
fun rememberNoteHost(
    note: String?,
    shown: () -> Unit,
): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(note) {
        note?.let {
            host.showSnackbar(it)
            shown()
        }
    }
    return host
}
