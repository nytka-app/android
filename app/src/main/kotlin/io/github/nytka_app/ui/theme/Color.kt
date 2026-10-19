package io.github.nytka_app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Linen and ink, from the Nytka design system's tokens.json. Every text pair is at least 4.5:1 in both themes.

private val LinenSurface = Color(0xFFF7F3EC)
private val InkNightSurface = Color(0xFF14151B)

val NytkaLight: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF33427E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE0F5),
        onPrimaryContainer = Color(0xFF1A2453),
        inversePrimary = Color(0xFFB8C2F3),
        secondary = Color(0xFF575B6E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E1EC),
        onSecondaryContainer = Color(0xFF1B1E2B),
        tertiary = Color(0xFF7A5600),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBE0A6),
        onTertiaryContainer = Color(0xFF2A1D00),
        background = LinenSurface,
        onBackground = Color(0xFF1C1E26),
        surface = LinenSurface,
        onSurface = Color(0xFF1C1E26),
        surfaceVariant = Color(0xFFE0D8CC),
        onSurfaceVariant = Color(0xFF4A4852),
        // Elevation stays tonal: a tint would turn raised surfaces indigo.
        surfaceTint = LinenSurface,
        inverseSurface = Color(0xFF2F3038),
        inverseOnSurface = Color(0xFFF1ECE3),
        error = Color(0xFFB0281C),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFCDAD4),
        onErrorContainer = Color(0xFF410702),
        outline = Color(0xFF77737D),
        outlineVariant = Color(0xFFCDC5B8),
        scrim = Color(0xFF000000),
        surfaceBright = LinenSurface,
        surfaceContainer = Color(0xFFECE6DC),
        surfaceContainerHigh = Color(0xFFE6DFD4),
        surfaceContainerHighest = Color(0xFFE0D8CC),
        surfaceContainerLow = Color(0xFFF2EDE5),
        surfaceContainerLowest = Color(0xFFFFFDF9),
        surfaceDim = Color(0xFFD9D1C4),
    )

val NytkaDark: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFB8C2F3),
        onPrimary = Color(0xFF1C2758),
        primaryContainer = Color(0xFF33427E),
        onPrimaryContainer = Color(0xFFDCE0F5),
        inversePrimary = Color(0xFF33427E),
        secondary = Color(0xFFC1C4D8),
        onSecondary = Color(0xFF2B2F40),
        secondaryContainer = Color(0xFF41455A),
        onSecondaryContainer = Color(0xFFE0E1EC),
        tertiary = Color(0xFFEDC15C),
        onTertiary = Color(0xFF3F2D00),
        tertiaryContainer = Color(0xFF5B4200),
        onTertiaryContainer = Color(0xFFFBE0A6),
        background = InkNightSurface,
        onBackground = Color(0xFFE9E4DA),
        surface = InkNightSurface,
        onSurface = Color(0xFFE9E4DA),
        surfaceVariant = Color(0xFF33353D),
        onSurfaceVariant = Color(0xFFC4C0C9),
        surfaceTint = InkNightSurface,
        inverseSurface = Color(0xFFE9E4DA),
        inverseOnSurface = Color(0xFF2F3038),
        error = Color(0xFFFFB4A8),
        onError = Color(0xFF690A03),
        errorContainer = Color(0xFF73261C),
        onErrorContainer = Color(0xFFFCDAD4),
        outline = Color(0xFF8E8A94),
        outlineVariant = Color(0xFF45444C),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF393B44),
        surfaceContainer = Color(0xFF1E1F27),
        surfaceContainerHigh = Color(0xFF282A32),
        surfaceContainerHighest = Color(0xFF33353D),
        surfaceContainerLow = Color(0xFF1A1B22),
        surfaceContainerLowest = Color(0xFF0E0F14),
        surfaceDim = InkNightSurface,
    )

/** Colours outside Material's roles. `capture` means audio is being captured now, and nothing else. */
@Immutable
data class NytkaColors(
    val capture: Color,
    val onCapture: Color,
    val captureContainer: Color,
    val onCaptureContainer: Color,
)

val NytkaLightExtras =
    NytkaColors(
        capture = Color(0xFF2C6A3B),
        onCapture = Color(0xFFFFFFFF),
        captureContainer = Color(0xFFCBE9CC),
        onCaptureContainer = Color(0xFF0A2A12),
    )

val NytkaDarkExtras =
    NytkaColors(
        capture = Color(0xFF8ED39C),
        onCapture = Color(0xFF003915),
        captureContainer = Color(0xFF1F5129),
        onCaptureContainer = Color(0xFFCBE9CC),
    )

val LocalNytkaColors = staticCompositionLocalOf { NytkaLightExtras }

/**
 * The wallpaper's scheme with the reserved meanings put back: `tertiary` (needs your decision) and `error`
 * (failed) keep Nytka's values, and the surface tint stays off.
 */
fun ColorScheme.keepingMeanings(nytka: ColorScheme): ColorScheme =
    copy(
        tertiary = nytka.tertiary,
        onTertiary = nytka.onTertiary,
        tertiaryContainer = nytka.tertiaryContainer,
        onTertiaryContainer = nytka.onTertiaryContainer,
        error = nytka.error,
        onError = nytka.onError,
        errorContainer = nytka.errorContainer,
        onErrorContainer = nytka.onErrorContainer,
        surfaceTint = surface,
    )
