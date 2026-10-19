package io.github.nytka_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Linen and ink in light and dark. [wallpaperColors] (a setting, off by default) lets Material's roles follow the
 * wallpaper; capture, decisions (tertiary) and failures (error) keep their colours either way.
 */
@Composable
fun NytkaTheme(
    wallpaperColors: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val nytka = if (darkTheme) NytkaDark else NytkaLight
    val colors =
        if (wallpaperColors) {
            val context = LocalContext.current
            (
                if (darkTheme) {
                    dynamicDarkColorScheme(
                        context,
                    )
                } else {
                    dynamicLightColorScheme(context)
                }
            ).keepingMeanings(nytka)
        } else {
            nytka
        }
    CompositionLocalProvider(
        LocalNytkaColors provides if (darkTheme) NytkaDarkExtras else NytkaLightExtras,
        LocalNytkaTypography provides NytkaTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = NytkaMaterialTypography,
            shapes = NytkaShapes,
            content = content,
        )
    }
}

@Composable
fun NytkaPreviewTheme(content: @Composable () -> Unit) {
    NytkaTheme(content = content)
}

/** Nytka's colours and type beside MaterialTheme's, such as `NytkaTheme.colors.capture`. */
object NytkaTheme {
    val colors: NytkaColors
        @Composable @ReadOnlyComposable
        get() = LocalNytkaColors.current

    val typography: NytkaTypography
        @Composable @ReadOnlyComposable
        get() = LocalNytkaTypography.current
}
