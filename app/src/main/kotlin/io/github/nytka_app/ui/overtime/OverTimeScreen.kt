package io.github.nytka_app.ui.overtime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Insight
import io.github.nytka_app.core.api.InsightProgress
import io.github.nytka_app.core.api.Insights
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.theme.ReadingColumn

/**
 * Over time: how many days the server has heard against how many it needs, then each insight. A locked one says what
 * it still needs, with a bar; an unlocked one its summary and, with two points or more, a line against the usual value.
 */
@Composable
fun OverTimeScreen(
    onOpenTopic: (String) -> Unit = {},
    viewModel: OverTimeViewModel = hiltViewModel(),
    rollups: RollupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summaries by rollups.state.collectAsStateWithLifecycle()
    ReadingColumn {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summaries.available) {
                item(key = "rollups") {
                    RollupSection(summaries, rollups::show, rollups::earlier, rollups::later, onOpenTopic)
                }
            }
            state.error?.let { error ->
                item(key = "error") { ListFailure(error, state.offline, onRetry = viewModel::refresh) }
            }
            if (state.offline) {
                item(key = "offline") {
                    ListState(
                        ListStateKind.Offline,
                        stringResource(R.string.list_offline_title),
                        body = stringResource(R.string.list_offline_body),
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
            }
            state.insights?.let { insights ->
                item(key = "days") { Days(insights) }
                if (insights.insights.isEmpty()) {
                    item(key = "empty") {
                        ListState(
                            ListStateKind.Empty,
                            stringResource(R.string.over_time_empty_title),
                            body = stringResource(R.string.over_time_empty_body),
                        )
                    }
                }
            }
            items(state.ordered, key = { it.id }) { insight -> InsightCard(insight) }
        }
    }
}

@Composable
private fun Days(insights: Insights) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (insights.trustedAfterDays > 0) {
            Text(
                stringResource(
                    R.string.over_time_days_format,
                    insights.daysWithAudio.coerceAtMost(insights.trustedAfterDays),
                    insights.trustedAfterDays,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(
                progress = { fraction(insights.daysWithAudio, insights.trustedAfterDays) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.over_time_trusted_format, insights.trustedAfterDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val locked = !insight.unlocked
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = if (locked) colors.surfaceContainer else colors.surfaceContainerHigh,
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(insight.title, style = MaterialTheme.typography.titleMedium)
            val progress = insight.progress
            if (locked && progress != null) {
                Text(
                    needs(progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { fraction(progress.have, progress.need) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!locked) {
                insight.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (insight.series.size >= 2) Trend(insight)
                insight.baseline?.let { baseline ->
                    Text(
                        stringResource(R.string.over_time_baseline_format, valueText(baseline, insight.unitLabel)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "Needs 6 more days"; any other unit as the server names it. */
@Composable
internal fun needs(progress: InsightProgress): String {
    val left = (progress.need - progress.have).coerceAtLeast(0)
    return if (progress.unit == DAYS) {
        pluralStringResource(R.plurals.over_time_needs_days, left, left)
    } else {
        stringResource(R.string.over_time_needs_format, left, progress.unit)
    }
}

/** The series as a line, with the usual value dashed across it; drawn only from two points on. */
@Composable
private fun Trend(insight: Insight) {
    val line = MaterialTheme.colorScheme.primary
    val usual = MaterialTheme.colorScheme.outline
    val values = insight.series.map { it.value }
    val low = minOf(values.min(), insight.baseline ?: values.min())
    val high = maxOf(values.max(), insight.baseline ?: values.max())
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val description = insight.series.joinToString { "${it.date}: ${valueText(it.value, insight.unitLabel)}" }
    Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
        fun y(value: Double) = (size.height * (1 - (value - low) / span)).toFloat()
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, value -> if (i == 0) path.moveTo(0f, y(value)) else path.lineTo(step * i, y(value)) }
        insight.baseline?.let { baseline ->
            drawLine(
                usual,
                Offset(0f, y(baseline)),
                Offset(size.width, y(baseline)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

internal fun fraction(
    have: Int,
    need: Int,
): Float = if (need <= 0) 1f else (have.toFloat() / need).coerceIn(0f, 1f)

private fun valueText(
    value: Double,
    unit: String?,
): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
    return listOfNotNull(number, unit).joinToString(" ")
}

private const val DAYS = "days"
