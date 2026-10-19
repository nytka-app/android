package io.github.nytka_app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A card that waits for the owner's answer: a saffron header with the question (it is a decision, not a problem,
 * so never red) over [content], which holds the evidence and the buttons.
 */
@Composable
fun DecisionCard(
    question: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val saffron = MaterialTheme.colorScheme.tertiaryContainer
    OutlinedCard(modifier.fillMaxWidth(), border = BorderStroke(1.dp, saffron)) {
        Surface(Modifier.fillMaxWidth(), color = saffron) {
            Text(
                question,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
