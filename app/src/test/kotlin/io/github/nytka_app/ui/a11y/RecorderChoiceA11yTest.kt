package io.github.nytka_app.ui.a11y

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.pendant.RecorderModel
import io.github.nytka_app.ui.RecorderChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class RecorderChoiceA11yTest {
    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf(RecorderModel.of(null))

    private fun show(
        fontScale: Float = 1f,
        dark: Boolean = false,
    ) = compose.setScreen(fontScale, dark) { RecorderChoice(selected, onSelect = { selected = it }) }

    private val unverified = hasText("Not verified on real hardware")

    @Test
    fun `Omi is selected at first and Limitless says nothing until it is picked`() {
        show()
        compose.onNodeWithText("Omi").assertIsSelected()
        compose.onNodeWithText("Limitless").assertIsNotSelected()
        compose.onAllNodes(unverified).assertCountEquals(0)
    }

    @Test
    fun `picking Limitless selects it and shows that it is not verified`() {
        show()
        compose.onNodeWithText("Limitless").performClick()
        assertEquals(RecorderModel.Limitless, selected)
        compose.onNodeWithText("Limitless").assertIsSelected()
        compose.onNodeWithText("Omi").assertIsNotSelected()
        compose.onNode(unverified).assertExists()
    }

    @Test
    fun `each choice is a radio button`() {
        show()
        RecorderModel.entries.forEach {
            compose.onNodeWithText(it.displayName).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            )
        }
    }

    @Test
    fun `clean at 1_0 in light`() {
        selected = RecorderModel.Limitless
        show()
        compose.assertClean()
    }

    @Test
    fun `clean at 2_0 in dark`() {
        selected = RecorderModel.Limitless
        show(2f, dark = true)
        compose.assertClean()
    }
}
