package io.github.nytka_app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R

/**
 * The Conversations tab's search icon shows only while this is true. A server without search answers 404 and the
 * screen says so.
 */
const val SEARCH_ENABLED = true

/**
 * Full-text search over conversations, memories and people. A hit opens through [onOpenConversation] or
 * [onOpenPerson].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenConversation: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                )
            }
            item {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                }
            }
            items(state.rows, key = { it.key }) { row ->
                ListItem(
                    overlineContent =
                        row.title?.let { title ->
                            { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                    headlineContent = {
                        Text(
                            snippetText(row.snippet),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text(row.date) },
                    modifier =
                        when {
                            row.personId != null -> Modifier.clickable { onOpenPerson(row.personId) }
                            row.openId != null -> Modifier.clickable { onOpenConversation(row.openId) }
                            else -> Modifier
                        },
                )
            }
            if (state.rows.isNotEmpty() && !state.endReached) {
                item(key = "more") { LaunchedEffect(state.rows.size) { viewModel.loadMore() } }
            }
            if (state.nothingFound) {
                item { Text(stringResource(R.string.nothing_found)) }
            }
        }
    }
}

private fun snippetText(spans: List<SnippetSpan>): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            if (span.bold) {
                withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                ) { append(span.text) }
            } else {
                append(span.text)
            }
        }
    }
