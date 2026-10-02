package io.github.nytka_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val RecordingRed = Color(0xFFD32F2F)

/** Red dot while recording, grey otherwise, and the pendant battery. */
@Composable
fun StatusChip(state: StatusUiState) {
    val label =
        when {
            !state.capture.running -> "Off"
            state.muted -> "Muted"
            state.capture.recording -> "Recording"
            else -> "Waiting"
        }
    val dot = if (state.capture.recording) RecordingRed else MaterialTheme.colorScheme.outline
    AssistChip(
        onClick = {},
        label = { Text(listOfNotNull(label, state.capture.battery?.let { "$it%" }).joinToString(" · ")) },
        leadingIcon = {
            Box(
                Modifier
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
        },
    )
}
