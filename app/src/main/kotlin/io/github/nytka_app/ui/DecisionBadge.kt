package io.github.nytka_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A count of things waiting for the owner's decision: a saffron rounded square, never Material's red badge, since
 * a decision is not a failure.
 */
@Composable
fun DecisionBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
