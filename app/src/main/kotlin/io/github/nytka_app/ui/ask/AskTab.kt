package io.github.nytka_app.ui.ask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R

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
    val linkColor = MaterialTheme.colorScheme.primary

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
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                if (state.asking) {
                    CircularProgressIndicator(
                        Modifier
                            .padding(start = 12.dp)
                            .size(24.dp),
                    )
                } else {
                    IconButton(onClick = viewModel::ask, enabled = state.canAsk) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_ask))
                    }
                }
            }
        }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        state.result?.let { result ->
            item { Text(answerText(result.parts, linkColor, onOpenConversation)) }
            if (result.sources.isNotEmpty()) {
                item { Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleSmall) }
                items(result.sources, key = { it.n }) { row ->
                    ListItem(
                        overlineContent = { Text(listOfNotNull("[${row.n}]", row.title).joinToString(" ")) },
                        headlineContent = { Text(row.snippet, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(row.date) },
                        modifier = row.openId?.let { id -> Modifier.clickable { onOpenConversation(id) } } ?: Modifier,
                    )
                }
            }
        }
    }
}

private fun answerText(
    parts: List<AnswerPart>,
    linkColor: Color,
    onOpen: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is AnswerPart.Text -> append(part.text)
                is AnswerPart.Cite ->
                    if (part.openId == null) {
                        append("[${part.n}]")
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "source-${part.n}",
                                styles =
                                    TextLinkStyles(
                                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                    ),
                            ) { onOpen(part.openId) },
                        ) { append("[${part.n}]") }
                    }
            }
        }
    }
