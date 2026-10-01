package io.github.nytka_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Material 3 with the wallpaper's colors; every supported Android version (12+) has dynamic color. */
@Composable
fun NytkaTheme(content: @Composable () -> Unit) {
    val colors = getColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Composable wrapper for nice previews. Not suitable for actual app
 */
@Composable
fun NytkaPreviewTheme(content: @Composable () -> Unit) {
    NytkaTheme {
        Surface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun getColorScheme(): ColorScheme {
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        isSystemInDarkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    return colorScheme
}