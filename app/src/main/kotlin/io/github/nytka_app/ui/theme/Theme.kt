package io.github.nytka_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 with the wallpaper's colors (every supported Android version, 12+, has dynamic color),
 * or with the brand scheme in [Color.kt] when [dynamicColor] is false.
 */
@Composable
fun NytkaTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colors =
        when {
            dynamicColor && dark -> dynamicDarkColorScheme(LocalContext.current)
            dynamicColor -> dynamicLightColorScheme(LocalContext.current)
            dark -> NytkaDarkColors
            else -> NytkaLightColors
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The brand scheme, so previews look the same on every machine. */
@Composable
fun NytkaPreviewTheme(content: @Composable () -> Unit) {
    NytkaTheme(dynamicColor = false, content = content)
}
