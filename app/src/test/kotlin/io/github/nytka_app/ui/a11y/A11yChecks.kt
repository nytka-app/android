package io.github.nytka_app.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.nytka_app.ui.theme.NytkaTheme

// Accessibility spec: the helpers the screen tests share. Each rule has a negative test in `A11yChecksTest`.

/** A node a person can act on: click, long-press, toggle. */
fun SemanticsNode.isActionable(): Boolean =
    config.contains(SemanticsActions.OnClick) ||
        config.contains(SemanticsActions.OnLongClick) ||
        config.contains(SemanticsProperties.ToggleableState)

/** What a screen reader says for this node on its own: description, then text, then selected or toggle state. */
fun SemanticsNode.spoken(): String =
    buildList {
        config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
        config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
    }.filter { it.isNotBlank() }.joinToString(", ")

private fun SemanticsNode.hidden(): Boolean = config.contains(SemanticsProperties.InvisibleToUser)

private fun SemanticsNode.descendants(): Sequence<SemanticsNode> =
    sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }

fun ComposeContentTestRule.actionableNodes(): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher("actionable") { it.isActionable() && !it.hidden() }).fetchSemanticsNodes()

/** Check 1: every actionable node has something to say. Returns one line per failure. */
fun ComposeContentTestRule.unnamed(): List<String> =
    actionableNodes().filter { it.spoken().isBlank() }.map { "node ${it.id} at ${it.boundsInRoot} has no name" }

/** Check 2: a toggle has a role, and a click with no words and no role is flagged. */
fun ComposeContentTestRule.badRoles(): List<String> =
    actionableNodes().mapNotNull { node ->
        val role = node.config.getOrNull(SemanticsProperties.Role)
        val toggle = node.config.contains(SemanticsProperties.ToggleableState)
        when {
            toggle && role == null -> "'${node.spoken()}' toggles but has no role"
            toggle && role == Role.Button -> "'${node.spoken()}' toggles but reads as a button"
            role == null && node.spoken().isBlank() -> "node ${node.id} is clickable, silent and has no role"
            else -> null
        }
    }

/** Check 3: every actionable node has a touch target of at least [minimum] dp each way. */
fun ComposeContentTestRule.smallTargets(minimum: Int = 48): List<String> {
    val density = density
    val floor = minimum * density.density
    return actionableNodes().mapNotNull { node ->
        val bounds = node.touchBoundsInRoot
        val tooSmall = bounds.width < floor - 0.5f || bounds.height < floor - 0.5f
        "'${node.spoken()}' touch target %.0f x %.0f dp (needs $minimum)"
            .format(
                bounds.width / density.density,
                bounds.height / density.density,
            ).takeIf { tooSmall }
    }
}

/** Check 4: text without a line limit that does not fit its box, and actionable nodes pushed past the screen's edge. */
fun ComposeContentTestRule.clipped(): List<String> {
    val failures = mutableListOf<String>()
    runOnIdle {
        val width = onRootWidth()
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
        texts.fetchSemanticsNodes().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            results.firstOrNull()?.let { layout ->
                val unlimited = layout.layoutInput.maxLines == Int.MAX_VALUE
                if (unlimited && (layout.didOverflowHeight || layout.widestLine() > layout.size.width + 1f)) {
                    failures += "text '${node.spoken().take(40)}' does not fit its box"
                }
            }
        }
        actionableNodes().filter { it.boundsInRoot.right > width + 1f }.forEach {
            failures += "'${it.spoken()}' reaches past the right edge of the screen"
        }
    }
    return failures
}

/** A word too long for its box overflows sideways; `didOverflowWidth` is also true for text shorter than its box. */
private fun TextLayoutResult.widestLine(): Float =
    (0 until lineCount).maxOfOrNull { getLineRight(it) - getLineLeft(it) } ?: 0f

private fun ComposeContentTestRule.onRootWidth(): Float =
    onAllNodes(SemanticsMatcher("root") { it.parent == null })
        .fetchSemanticsNodes()
        .first()
        .boundsInRoot.right

/**
 * Check 8: what a screen reader steps through, top to bottom: each node that speaks, with its role and heading mark.
 */
fun ComposeContentTestRule.spokenOrder(): List<String> {
    val root = onAllNodes(SemanticsMatcher("root") { it.parent == null }).fetchSemanticsNodes().first()
    return root
        .descendants()
        .filter { !it.hidden() && it.spoken().isNotBlank() }
        .map { node ->
            val role =
                node.config
                    .getOrNull(SemanticsProperties.Role)
                    ?.let { " [$it]" }
                    .orEmpty()
            val heading = if (node.config.contains(SemanticsProperties.Heading)) " [heading]" else ""
            node.spoken() + role + heading
        }.toList()
}

/** Check 10: nodes that announce themselves. */
fun ComposeContentTestRule.liveRegions(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNodes().map { it.spoken() }

/** Hosts [content] in the Nytka theme at [fontScale], light or dark. */
fun ComposeContentTestRule.setScreen(
    fontScale: Float = 1f,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            NytkaTheme(darkTheme = dark, content = content)
        }
    }
}

/** All of checks 1 to 4, as one list so a screen test reports every finding at once. */
fun ComposeContentTestRule.findings(): List<String> = unnamed() + badRoles() + smallTargets() + clipped()

/** Fails with every finding and the spoken order, so a reviewer sees what a screen reader would walk through. */
fun ComposeContentTestRule.assertClean() {
    val found = findings()
    if (found.isNotEmpty()) {
        throw AssertionError((found + "ORDER:" + spokenOrder()).joinToString("\n"))
    }
}
