package io.github.nytka_app.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.theme.NytkaTheme
import io.github.nytka_app.ui.theme.ReadingColumn
import io.github.nytka_app.ui.theme.TABULAR

/**
 * The Ask tab: a question about your history, answered with numbered sources. A source, or its `[n]` in the answer,
 * opens its conversation through [onOpenConversation].
 */
@Composable
fun AskTab(
    onOpenConversation: (String) -> Unit,
    viewModel: AskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReadingColumn {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.question,
                        onValueChange = viewModel::setQuestion,
                        label = { Text(stringResource(R.string.ask_hint)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    IconButton(onClick = viewModel::ask, enabled = state.canAsk) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.action_ask),
                        )
                    }
                }
            }
            if (state.asking) {
                item(key = "asking") {
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.ask_asking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            if (state.result == null && state.error == null && !state.asking) {
                item { ListState(ListStateKind.Empty, stringResource(R.string.ask_empty)) }
            }
            state.error?.let { error ->
                item { ListFailure(error, offline = false, onRetry = viewModel::ask) }
            }
            state.result?.let { result ->
                if (result.question.isNotBlank()) {
                    item(key = "question") { QuestionBubble(result.question) }
                }
                if (result.sources.isEmpty()) {
                    // An answer no source supports is not shown; the sentence says so plainly.
                    item(key = "none") {
                        Text(
                            stringResource(R.string.ask_no_answer),
                            style = NytkaTheme.typography.readingLead,
                        )
                    }
                } else {
                    item(key = "answer") { AnswerText(result.parts, onOpenConversation) }
                    item { Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleSmall) }
                    items(result.sources, key = { it.n }) { row ->
                        ListItem(
                            leadingContent = { CitationPill(row.n, onClick = null) },
                            overlineContent = row.title?.let { title -> { Text(title) } },
                            headlineContent = {
                                Text(
                                    stringResource(R.string.source_quote, row.snippet),
                                    style = NytkaTheme.typography.readingQuote,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = { Text(row.date) },
                            modifier =
                                row.openId?.let { id ->
                                    Modifier.clickable(role = Role.Button) { onOpenConversation(id) }
                                } ?: Modifier,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** The owner's question, right-aligned on a raised surface. */
@Composable
private fun QuestionBubble(question: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            Modifier.widthIn(max = 320.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
        }
    }
}

/** The answer in reading type, each `[n]` a [CitationPill] that opens its conversation. */
@Composable
private fun AnswerText(
    parts: List<AnswerPart>,
    onOpen: (String) -> Unit,
) {
    val cites = parts.filterIsInstance<AnswerPart.Cite>().distinctBy { it.n }
    val inline =
        cites.associate { cite ->
            val width = if (cite.n >= PILL_WIDE) PILL_WIDTH_WIDE else PILL_WIDTH
            "cite-${cite.n}" to
                InlineTextContent(Placeholder(width, PILL_HEIGHT, PlaceholderVerticalAlign.TextCenter)) {
                    CitationPill(cite.n, cite.openId?.let { id -> { onOpen(id) } })
                }
        }
    val text =
        buildAnnotatedString {
            parts.forEach { part ->
                when (part) {
                    is AnswerPart.Text -> append(part.text)
                    is AnswerPart.Cite -> appendInlineContent("cite-${part.n}", "[${part.n}]")
                }
            }
        }
    Text(text, style = NytkaTheme.typography.readingLead, inlineContent = inline)
}

private const val PILL_WIDE = 100
private val PILL_WIDTH = 26.sp
private val PILL_WIDTH_WIDE = 34.sp
private val PILL_HEIGHT = 20.sp

/** A source number: a `primary-container` pill, a button when it [onClick]s to a conversation. */
@Composable
private fun CitationPill(
    n: Int,
    onClick: (() -> Unit)?,
) {
    val label = stringResource(R.string.ask_source_format, n)
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .let {
                if (onClick != null) {
                    it.clickable(
                        onClickLabel = stringResource(R.string.action_open),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    it
                }
            }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
