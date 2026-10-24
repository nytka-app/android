package io.github.nytka_app.ui.overtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Rollup
import io.github.nytka_app.core.api.TopicRef
import io.github.nytka_app.ui.today.TopicChips
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Summaries over a week, a month, a year and all time: the period's dates, the summary, what changed since the
 * period before, its topics and what it was made from. ‹ and › step through earlier summaries of the period.
 */
@Composable
fun RollupSection(
    state: RollupsUiState,
    onPeriod: (String) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Rollup.PERIODS.forEachIndexed { i, period ->
                SegmentedButton(
                    selected = state.period == period,
                    onClick = { onPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(i, Rollup.PERIODS.size),
                    label = { Text(stringResource(labelOf(period)), maxLines = 1) },
                )
            }
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.rollup?.let(::rangeOf).orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(
                        onClick = onEarlier,
                        enabled =
                            state.rollup != null && state.canGoEarlier && !state.loading,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.rollup_previous))
                    }
                    IconButton(onClick = onLater, enabled = state.canGoLater) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.rollup_next))
                    }
                }
                val rollup = state.rollup
                when {
                    rollup == null && !state.loading ->
                        Text(stringResource(R.string.rollup_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)

                    rollup == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    rollup.locked -> Locked(rollup)
                    else -> Open(rollup, onOpenTopic)
                }
            }
        }
    }
}

@Composable
private fun Locked(rollup: Rollup) {
    val progress = rollup.progress ?: return
    Text(
        needs(progress),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(progress = { fraction(progress.have, progress.need) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Open(
    rollup: Rollup,
    onOpenTopic: (String) -> Unit,
) {
    rollup.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    if (rollup.changes.isNotEmpty()) {
        Text(stringResource(R.string.rollup_changes), style = MaterialTheme.typography.titleSmall)
        rollup.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
    TopicChips(rollup.topics.map { TopicRef(it.topicId, it.name) }, onOpenTopic)
    val sources = rollup.sources
    if (sources.conversations > 0) {
        Text(
            stringResource(
                R.string.rollup_sources_format,
                pluralStringResource(R.plurals.rollup_conversations, sources.conversations, sources.conversations),
                pluralStringResource(R.plurals.rollup_digests, sources.digests, sources.digests),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun labelOf(period: String): Int =
    when (period) {
        Rollup.MONTH -> R.string.rollup_month
        Rollup.YEAR -> R.string.rollup_year
        Rollup.ALL -> R.string.rollup_all
        else -> R.string.rollup_week
    }

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "19 Oct – 25 Oct 2026"; a range across years names both. */
internal fun rangeOf(rollup: Rollup): String {
    val start = runCatching { LocalDate.parse(rollup.start) }.getOrNull() ?: return rollup.start
    val end = runCatching { LocalDate.parse(rollup.end) }.getOrNull() ?: return rollup.start
    val first = if (start.year == end.year) dayMonth.format(start) else dayMonthYear.format(start)
    return "$first – ${dayMonthYear.format(end)}"
}
