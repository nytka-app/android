package io.github.nytka_app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.TextFieldValue
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
    supporting: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val look = FieldLook(label, error, isError, supporting, enabled, visualTransformation, trailingIcon)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fieldSize(),
        textStyle = fieldStyle(mono),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        singleLine = true,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner -> Decoration(value, inner, interaction, look) },
    )
}

/** The same field holding a [TextFieldValue], for callers that move the cursor, as a picked name does. */
@Composable
fun NytkaTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val look = FieldLook(label, error, error != null, supporting, enabled, VisualTransformation.None, trailingIcon)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fieldSize(),
        textStyle = fieldStyle(mono = false),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        decorationBox = { inner -> Decoration(value.text, inner, interaction, look) },
    )
}

private class FieldLook(
    val label: String,
    val error: String?,
    val isError: Boolean,
    val supporting: String?,
    val enabled: Boolean,
    val visualTransformation: VisualTransformation,
    val trailingIcon: @Composable (() -> Unit)?,
)

private fun Modifier.fieldSize() =
    defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight)

@Composable
private fun fieldStyle(mono: Boolean) =
    (if (mono) NytkaTheme.typography.code else LocalTextStyle.current)
        .copy(color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Decoration(
    text: String,
    inner: @Composable () -> Unit,
    interaction: MutableInteractionSource,
    look: FieldLook,
) {
    val colors = OutlinedTextFieldDefaults.colors()
    OutlinedTextFieldDefaults.DecorationBox(
        value = text,
        innerTextField = inner,
        enabled = look.enabled,
        singleLine = true,
        visualTransformation = look.visualTransformation,
        interactionSource = interaction,
        isError = look.isError,
        label = { Text(look.label) },
        trailingIcon = look.trailingIcon,
        supportingText =
            when {
                look.error != null -> {
                    { ErrorLine(look.error) }
                }
                look.supporting != null -> {
                    { Text(look.supporting) }
                }
                else -> null
            },
        colors = colors,
        container = {
            OutlinedTextFieldDefaults.Container(
                enabled = look.enabled,
                isError = look.isError,
                interactionSource = interaction,
                colors = colors,
                shape = MaterialTheme.shapes.small,
                focusedBorderThickness = 3.dp,
                unfocusedBorderThickness = if (look.isError) 2.dp else 1.dp,
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
