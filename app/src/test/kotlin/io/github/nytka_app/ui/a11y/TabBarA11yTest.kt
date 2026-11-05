package io.github.nytka_app.ui.a11y

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.ui.LifeNavHost
import io.github.nytka_app.ui.LifeScreens
import io.github.nytka_app.ui.NytkaNavHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom bar is inline in both hosts, so the hosts run here with a line of text for each screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class TabBarA11yTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showThree(
        fontScale: Float,
        dark: Boolean,
    ) = compose.setScreen(fontScale, dark) {
        LifeNavHost(
            screens =
                LifeScreens(
                    today = { Text(SCREEN) },
                    timeline = { Text("Timeline screen") },
                    overTime = { Text("Over time screen") },
                    conversation = {},
                    conversations = { _, _ -> },
                    person = {},
                    people = {},
                    tasks = {},
                    memories = {},
                    ask = {},
                    search = {},
                    topics = {},
                    topic = {},
                    device = {},
                ),
            topBar = { _, _, _ -> },
        )
    }

    private fun showFive(
        fontScale: Float,
        dark: Boolean,
        peopleBadge: Int = 0,
    ) = compose.setScreen(fontScale, dark) {
        NytkaNavHost(
            conversations = { _, _ -> Text(SCREEN) },
            tasks = { _, _ -> },
            memories = { _, _ -> },
            people = { _, _ -> },
            ask = { _, _ -> },
            device = {},
            peopleBadge = peopleBadge,
        )
    }

    @Test
    fun `three tabs at 1_0 in light`() {
        showThree(1f, false)
        compose.assertClean()
    }

    @Test
    fun `three tabs at 2_0 in dark`() {
        showThree(2f, true)
        compose.assertClean()
    }

    @Test
    fun `five tabs at 1_0 in light`() {
        showFive(1f, false)
        compose.assertClean()
    }

    @Test
    fun `five tabs at 1_0 in dark`() {
        showFive(1f, true)
        compose.assertClean()
    }

    @Test
    fun `five tabs at 1_3 in light`() {
        showFive(1.3f, false)
        compose.assertClean()
    }

    @Test
    fun `five tabs at 2_0 in light`() {
        showFive(2f, false)
        compose.assertClean()
    }

    @Test
    fun `five tabs at 2_0 in dark`() {
        showFive(2f, true)
        compose.assertClean()
    }

    @Test
    fun `five tabs with open decisions at 2_0`() {
        showFive(2f, false, peopleBadge = 3)
        compose.assertClean()
    }

    @Test
    fun `every tab is a tab and the shown one is selected`() {
        showFive(1f, false)
        val tabs = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)).fetchSemanticsNodes()
        assertEquals(5, tabs.count { it.config[SemanticsProperties.Role].toString() == "Tab" })
        compose.onNodeWithText("Conversations").assertIsSelected()
    }

    @Test
    fun `open decisions are spoken on the People tab`() {
        showFive(1f, false, peopleBadge = 3)
        compose
            .onNodeWithText("People")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 need you"))
    }

    @Test
    fun `the five tabs read in this order`() {
        showFive(1f, false, peopleBadge = 3)
        assertEquals(
            listOf("A screen", "Conversations [Tab]", "Tasks [Tab]", "Ask [Tab]", "People [Tab]", "Memories [Tab]"),
            compose.spokenOrder(),
        )
    }

    @Test
    fun `the three tabs read in this order`() {
        showThree(1f, false)
        assertEquals(listOf("A screen", "Today [Tab]", "Timeline [Tab]", "Over time [Tab]"), compose.spokenOrder())
    }

    private companion object {
        const val SCREEN = "A screen"
    }
}
