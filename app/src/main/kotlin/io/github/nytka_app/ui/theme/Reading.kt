package io.github.nytka_app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** About 64 characters of 17sp Literata plus gutters, a time column and a row icon. */
val ReadingMaxWidth: Dp = 640.dp

/** Centres [content] in a column of at most [ReadingMaxWidth]: wide screens add margin, not line length. */
@Composable
fun ReadingColumn(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ReadingMaxWidth).fillMaxSize()) { content() }
    }
}
