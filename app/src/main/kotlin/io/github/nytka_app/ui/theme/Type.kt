package io.github.nytka_app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.nytka_app.R

// Bundled variable fonts (SIL OFL 1.1), subset to Latin, Latin Extended and Cyrillic: the F-Droid build has no
// Play services for downloadable fonts. Onest carries weights 400 to 600, Literata 400 to 500 at opsz 12 to 28.

private fun onest(weight: Int) =
    Font(
        R.font.onest_variable,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val Onest = FontFamily(onest(400), onest(500), onest(600))

/** Literata with its optical size set to the text size, as the design asks. Upright only: no italic is bundled. */
private fun literata(
    weight: Int,
    size: Float,
) = FontFamily(
    Font(
        R.font.literata_variable,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", size)),
    ),
)

private val JetBrainsMono =
    FontFamily(
        Font(
            R.font.jetbrains_mono_variable,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
    )

/** Tabular figures, so times, durations and counts don't shift as they change. */
const val TABULAR = "tnum"

private fun ui(
    size: Int,
    line: Int,
    weight: Int,
    tracking: TextUnit = 0.sp,
    features: String? = null,
) = TextStyle(
    fontFamily = Onest,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking,
    fontFeatureSettings = features,
)

val NytkaMaterialTypography =
    Typography(
        displayLarge = ui(57, 64, 500, (-0.5).sp),
        displayMedium = ui(45, 52, 500),
        displaySmall = ui(36, 44, 500, (-0.25).sp, TABULAR),
        headlineLarge = ui(32, 40, 500),
        headlineMedium = ui(28, 36, 500),
        headlineSmall = ui(24, 32, 500),
        titleLarge = ui(22, 28, 500),
        titleMedium = ui(16, 24, 600, 0.1.sp),
        titleSmall = ui(14, 20, 600, 0.1.sp),
        bodyLarge = ui(16, 24, 400),
        bodyMedium = ui(14, 20, 400, 0.1.sp),
        bodySmall = ui(12, 16, 400, 0.2.sp, TABULAR),
        labelLarge = ui(14, 20, 600, 0.1.sp),
        labelMedium = ui(12, 16, 600, 0.3.sp, TABULAR),
        // 12sp, not M3's 11sp: Ukrainian labels at 11sp lose the counters of и, к and ж.
        labelSmall = ui(12, 16, 500, 0.3.sp, TABULAR),
    )

/** Long text (Literata) and developer strings (JetBrains Mono); UI chrome uses MaterialTheme.typography. */
@Immutable
data class NytkaTypography(
    val readingTitle: TextStyle,
    val readingLead: TextStyle,
    val readingBody: TextStyle,
    val readingQuote: TextStyle,
    val code: TextStyle,
)

private fun reading(
    size: Int,
    line: Int,
    weight: Int,
) = TextStyle(
    fontFamily = literata(weight, size.toFloat()),
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

val NytkaTextStyles =
    NytkaTypography(
        readingTitle = reading(26, 34, 500),
        readingLead = reading(18, 28, 400),
        // At most 64 characters a line: wider screens add margin, not width.
        readingBody = reading(17, 27, 400),
        // Quotes stay upright inside quotation marks; nothing may ask for italic.
        readingQuote = reading(15, 24, 400),
        // JetBrains Mono has no ₴: never set money in it.
        code = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 20.sp),
    )

val LocalNytkaTypography = staticCompositionLocalOf { NytkaTextStyles }
