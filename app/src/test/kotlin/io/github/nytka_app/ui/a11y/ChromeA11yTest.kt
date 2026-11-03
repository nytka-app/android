package io.github.nytka_app.ui.a11y

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.R
import io.github.nytka_app.capture.CaptureState
import io.github.nytka_app.ui.CaptureStatusIndicator
import io.github.nytka_app.ui.CaptureStatusStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class ChromeA11yTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        fontScale: Float,
        dark: Boolean,
    ) = compose.setScreen(fontScale, dark) {
        Column {
            CaptureState.entries.forEach { state ->
                CaptureStatusIndicator(state, onClick = {})
                CaptureStatusIndicator(state, style = CaptureStatusStyle.Strip, detail = DETAIL)
            }
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
    ) {
        show(fontScale, dark)
        compose.assertClean()
    }

    @Test
    fun `capture status at 1_0 in light`() = clean(1f)

    @Test
    fun `capture status at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `capture status at 2_0 in light`() = clean(2f)

    @Test
    fun `capture status at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `every state says its sentence in a polite live region`() {
        show(1f, false)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sentences =
            listOf(
                R.string.capture_sentence_recording,
                R.string.capture_sentence_muted,
                R.string.capture_sentence_waiting,
                R.string.capture_sentence_off,
            ).map(context::getString)
        val expected = sentences.flatMap { listOf(it, "$it. $DETAIL") }
        assertEquals(expected, compose.liveRegions())
    }

    private companion object {
        const val DETAIL = "Pendant 67% · 0 queued"
    }
}
