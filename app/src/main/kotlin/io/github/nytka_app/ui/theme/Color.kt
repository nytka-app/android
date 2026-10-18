package io.github.nytka_app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The brand scheme from docs/design/tokens.md: ink indigo, paper and madder, seeded from the launcher icon. */
val NytkaLightColors =
    lightColorScheme(
        primary = Color(0xFF3B3F8F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE1E0FF),
        onPrimaryContainer = Color(0xFF14175C),
        secondary = Color(0xFF5C5D72),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE2E0F9),
        onSecondaryContainer = Color(0xFF191A2C),
        tertiary = Color(0xFF9A4521),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBCD),
        onTertiaryContainer = Color(0xFF3A0F00),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF8F3),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8F3),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE4E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2EC),
        surfaceContainer = Color(0xFFEFECE6),
        surfaceContainerHigh = Color(0xFFE9E6E1),
        surfaceContainerHighest = Color(0xFFE4E1DB),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC8C5D0),
    )

val NytkaDarkColors =
    darkColorScheme(
        primary = Color(0xFFBFC2FF),
        onPrimary = Color(0xFF23286B),
        primaryContainer = Color(0xFF3A3E87),
        onPrimaryContainer = Color(0xFFE1E0FF),
        secondary = Color(0xFFC5C4DD),
        onSecondary = Color(0xFF2E2F42),
        secondaryContainer = Color(0xFF444559),
        onSecondaryContainer = Color(0xFFE2E0F9),
        tertiary = Color(0xFFFFB598),
        onTertiary = Color(0xFF5A1C00),
        tertiaryContainer = Color(0xFF7B2E0C),
        onTertiaryContainer = Color(0xFFFFDBCD),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC7C5D0),
        surfaceContainerLowest = Color(0xFF0D0E13),
        surfaceContainerLow = Color(0xFF1A1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF292A30),
        surfaceContainerHighest = Color(0xFF34343B),
        outline = Color(0xFF918F9A),
        outlineVariant = Color(0xFF46464F),
    )
