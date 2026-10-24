package io.github.nytka_app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.RelatedMoment
import io.github.nytka_app.core.api.TopicRef
import io.github.nytka_app.ui.conversations.Formatting
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Earlier": earlier moments about the same thing, best first, as a row of cards with the day, the conversation's
 * title, the line and why it is related. A card opens the conversation at that moment. Nothing shows without moments.
 */
@Composable
fun EarlierStrip(
    moments: List<RelatedMoment>,
    zone: ZoneId,
    today: LocalDate,
    onOpenMoment: (conversationId: String, atMs: Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (moments.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.earlier_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(contentPadding).semantics { heading() },
        )
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(moments, key = { "${it.conversationId}-${it.at}" }) { moment ->
                val at = Formatting.parse(moment.at)
                Card(
                    onClick = { onOpenMoment(moment.conversationId, at?.toEpochMilli()) },
                    modifier = Modifier.width(CARD_WIDTH.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        at?.let {
                            Text(
                                "${Formatting.dayTitle(
                                    it.atZone(zone).toLocalDate(),
                                    today,
                                )} · ${Formatting.clock(it, zone)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        moment.title?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        moment.quote?.let {
                            Text(
                                "“$it”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                maxLines = QUOTE_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        why(moment)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Ann · lease": the people and tags the moment shares, or "Same subject" when only the meaning does. */
@Composable
private fun why(moment: RelatedMoment): String? {
    val shared = moment.people + moment.tags
    return when {
        shared.isNotEmpty() -> shared.joinToString(" · ")
        "meaning" in moment.reasons -> stringResource(R.string.earlier_same_subject)
        else -> null
    }
}

/** The topic pages a thing belongs to, as chips that open them. Nothing shows without topics. */
@Composable
fun TopicChips(
    topics: List<TopicRef>,
    onOpenTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (topics.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(topics, key = { it.id }) { topic ->
            AssistChip(onClick = { onOpenTopic(topic.id) }, label = { Text(topic.name) })
        }
    }
}

/** The earlier moments of the screen's conversation, read only where the layout can open them ([enabled]). */
@Composable
fun rememberEarlier(
    related: RelatedViewModel,
    enabled: Boolean,
): List<RelatedMoment> {
    val earlier by related.moments.collectAsStateWithLifecycle()
    LaunchedEffect(related.routeConversation, enabled) {
        val id = related.routeConversation?.takeIf { enabled } ?: return@LaunchedEffect
        related.load(RelatedViewModel.KIND_CONVERSATION, id)
    }
    return earlier
}

/** The "Earlier" strip under the summary, when there are moments and a way to open them. */
fun LazyListScope.earlierItem(
    earlier: List<RelatedMoment>,
    onOpenMoment: ((String, Long?) -> Unit)?,
) {
    if (onOpenMoment == null || earlier.isEmpty()) return
    item(key = "earlier") { EarlierStrip(earlier, ZoneId.systemDefault(), LocalDate.now(), onOpenMoment) }
}

private const val CARD_WIDTH = 260
private const val QUOTE_LINES = 3
