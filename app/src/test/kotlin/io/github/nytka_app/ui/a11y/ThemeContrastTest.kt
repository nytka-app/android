package io.github.nytka_app.ui.a11y

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.nytka_app.ui.theme.NytkaColors
import io.github.nytka_app.ui.theme.NytkaDark
import io.github.nytka_app.ui.theme.NytkaDarkExtras
import io.github.nytka_app.ui.theme.NytkaLight
import io.github.nytka_app.ui.theme.NytkaLightExtras
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accessibility spec checks 5 and 6: text pairs at 4.5:1, parts of controls at 3:1, in both themes. */
class ThemeContrastTest {
    private val text = 4.5
    private val control = 3.0

    private fun ColorScheme.textPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "onPrimary/primary" to (onPrimary to primary),
            "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
            "onSecondary/secondary" to (onSecondary to secondary),
            "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
            "onTertiary/tertiary" to (onTertiary to tertiary),
            "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
            "onError/error" to (onError to error),
            "onErrorContainer/errorContainer" to (onErrorContainer to errorContainer),
            "onBackground/background" to (onBackground to background),
            "onSurface/surface" to (onSurface to surface),
            "onSurfaceVariant/surfaceVariant" to (onSurfaceVariant to surfaceVariant),
            "inverseOnSurface/inverseSurface" to (inverseOnSurface to inverseSurface),
            "inversePrimary/inverseSurface" to (inversePrimary to inverseSurface),
        ) +
            surfaceTiers().flatMap { (name, tier) ->
                listOf(
                    "onSurface/$name" to (onSurface to tier),
                    "onSurfaceVariant/$name" to (onSurfaceVariant to tier),
                )
            }

    private fun ColorScheme.surfaceTiers(): Map<String, Color> =
        mapOf(
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceVariant" to surfaceVariant,
        )

    private fun NytkaColors.textPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "onCapture/capture" to (onCapture to capture),
            "onCaptureContainer/captureContainer" to (onCaptureContainer to captureContainer),
        )

    private fun check(
        theme: String,
        pairs: Map<String, Pair<Color, Color>>,
        minimum: Double,
    ) {
        val failures =
            pairs.mapNotNull { (name, colours) ->
                val ratio = Contrast.ratio(colours.first, colours.second)
                "$theme $name %.2f:1 (needs $minimum:1)".format(ratio).takeIf { ratio < minimum }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `light text pairs reach 4_5 to 1`() {
        check("light", NytkaLight.textPairs() + NytkaLightExtras.textPairs(), text)
    }

    @Test
    fun `dark text pairs reach 4_5 to 1`() {
        check("dark", NytkaDark.textPairs() + NytkaDarkExtras.textPairs(), text)
    }

    private fun controlPairs(
        scheme: ColorScheme,
        extras: NytkaColors,
    ): Map<String, Pair<Color, Color>> =
        mapOf(
            "outline/surface" to (scheme.outline to scheme.surface),
            "primary/surface" to (scheme.primary to scheme.surface),
            "error/surface" to (scheme.error to scheme.surface),
            "tertiary/surface" to (scheme.tertiary to scheme.surface),
            "capture/surface" to (extras.capture to scheme.surface),
        )

    @Test
    fun `light parts of controls reach 3 to 1`() {
        check("light", controlPairs(NytkaLight, NytkaLightExtras), control)
    }

    @Test
    fun `dark parts of controls reach 3 to 1`() {
        check("dark", controlPairs(NytkaDark, NytkaDarkExtras), control)
    }

    @Test
    fun `the check fails on a pair that is too close`() {
        val failing = mapOf("grey/white" to (Color(0xFFAAAAAA) to Color.White))
        val result = runCatching { check("test", failing, text) }

        assertTrue(result.exceptionOrNull() is AssertionError)
    }
}
