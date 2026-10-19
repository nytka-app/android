package io.github.nytka_app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.nytka_app.ui.theme.NytkaTheme

/**
 * The design system's TextField: a visible label, `Shapes.small`, a 3dp `primary` border in focus and a 2dp `error`
 * border with a triangle and a sentence under it. [mono] sets addresses and tokens in JetBrains Mono.
 */
@Composable
fun NytkaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    error: String? = null,
    isError: Boolean = error != null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    val textStyle = (if (mono) NytkaTheme.typography.code else LocalTextStyle.current)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier.defaultMinSize(
                minWidth = OutlinedTextFieldDefaults.MinWidth,
                minHeight = OutlinedTextFieldDefaults.MinHeight,
            ),
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = true,
                singleLine = true,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                isError = isError,
                label = { Text(label) },
                trailingIcon = trailingIcon,
                supportingText = error?.let { { ErrorLine(it) } },
                colors = colors,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interaction,
                        colors = colors,
                        shape = MaterialTheme.shapes.small,
                        focusedBorderThickness = 3.dp,
                        unfocusedBorderThickness = if (isError) 2.dp else 1.dp,
                    )
                },
            )
        },
    )
}

/** A failure in words, with the triangle so it is not only red. */
@Composable
fun ErrorLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp).align(Alignment.Top),
        )
        Text(text, color = MaterialTheme.colorScheme.error)
    }
}
