package io.github.nytka_app.ui.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each rule must fail on a node that breaks it, or a green screen test proves nothing. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class A11yChecksTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a clickable with no words is unnamed`() {
        compose.setScreen { Box(Modifier.size(60.dp).clickable {}) }
        assertEquals(1, compose.unnamed().size)
    }

    @Test
    fun `a described clickable is named`() {
        compose.setScreen { Box(Modifier.size(60.dp).semantics { contentDescription = "Open" }.clickable {}) }
        assertTrue(compose.unnamed().isEmpty())
    }

    @Test
    fun `a toggle with no role is flagged`() {
        compose.setScreen {
            Box(
                Modifier
                    .size(
                        60.dp,
                    ).semantics { contentDescription = "Mute" }
                    .toggleable(value = true, onValueChange = {}),
            )
        }
        assertEquals(1, compose.badRoles().size)
    }

    @Test
    fun `compose widens a small clickable to the 48 dp the platform guarantees`() {
        compose.setScreen { Box(Modifier.size(32.dp).semantics { contentDescription = "Small" }.clickable {}) }
        assertTrue(compose.smallTargets().isEmpty())
    }

    @Test
    fun `a 32 dp target is too small where nothing widens it`() {
        compose.setContent {
            val base = LocalViewConfiguration.current
            val narrow =
                object : ViewConfiguration by base {
                    override val minimumTouchTargetSize = DpSize.Zero
                }
            CompositionLocalProvider(LocalViewConfiguration provides narrow) {
                Box(Modifier.size(32.dp).semantics { contentDescription = "Small" }.clickable {})
                Box(Modifier.size(60.dp).semantics { contentDescription = "Large" }.clickable {})
            }
        }
        val small = compose.smallTargets()
        assertEquals(1, small.size)
        assertTrue(small.single(), small.single().contains("Small"))
    }

    @Test
    fun `text that does not fit its box is clipped`() {
        compose.setScreen {
            Box(
                Modifier.height(10.dp).width(80.dp),
            ) { Text("A long line of words that needs several lines") }
        }
        assertEquals(1, compose.clipped().size)
    }

    @Test
    fun `text that fits is not clipped`() {
        compose.setScreen { Text("Short") }
        assertTrue(compose.clipped().isEmpty())
    }
}
