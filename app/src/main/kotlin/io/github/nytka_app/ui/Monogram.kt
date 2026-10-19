package io.github.nytka_app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A person's first letter in a quiet disc. One colour for everyone: a person's colour would be a speaker
 * colour, which the design forbids. A name that is not a name yet shows `?`.
 */
@Composable
fun Monogram(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Surface(
        modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initialOf(name),
                style =
                    if (size >=
                        LARGE
                    ) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
            )
        }
    }
}

private val LARGE = 56.dp

/** The first letter of the first word, upper-cased; `?` for a blank name. */
fun initialOf(name: String): String =
    name
        .trim()
        .firstOrNull(Char::isLetterOrDigit)
        ?.uppercaseChar()
        ?.toString() ?: "?"
