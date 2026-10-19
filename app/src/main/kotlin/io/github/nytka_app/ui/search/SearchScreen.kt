package io.github.nytka_app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.names.NameCompletions
import io.github.nytka_app.ui.names.knownPeople
import io.github.nytka_app.ui.names.rememberFieldValue

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
    var query by rememberFieldValue(state.query)
    val people = knownPeople()
    val onQuery = { value: TextFieldValue ->
        query = value
        viewModel.setQuery(value.text)
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
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
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                NytkaTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = stringResource(R.string.search_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    trailingIcon =
                        if (query.text.isEmpty()) {
                            null
                        } else {
                            {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.search_clear),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .focusRequester(focus),
                )
            }
            item {
                NameCompletions(
                    query,
                    onValueChange = onQuery,
                    people = people,
                    capitalOnly = false,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                )
            }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(stringResource(filter.label)) },
                            leadingIcon =
                                if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
            searchStateItem(state, viewModel::retry)
            items(state.rows, key = { it.key }) { row ->
                val open: (() -> Unit)? =
                    when {
                        row.personId != null -> ({ onOpenPerson(row.personId) })
                        row.openId != null -> ({ onOpenConversation(row.openId) })
                        else -> null
                    }
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
                    leadingContent =
                        row.personId?.let {
                            { Monogram(row.title ?: row.snippet.joinToString("") { span -> span.text }) }
                        },
                    modifier = if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.rows.isNotEmpty() && !state.endReached) {
                item(key = "more") { LaunchedEffect(state.rows.size) { viewModel.loadMore() } }
            }
        }
    }
}

/** What the list says when it has no rows: a failure, a search under way, no hit, or the first prompt. */
private fun LazyListScope.searchStateItem(
    state: SearchUiState,
    onRetry: () -> Unit,
) {
    val error = state.error
    when {
        error != null -> item(key = "error") { ListFailure(error, state.offline, onRetry = onRetry) }

        state.rows.isEmpty() && state.searching ->
            item(key = "searching") { ListState(ListStateKind.Loading, stringResource(R.string.search_searching)) }

        state.nothingFound ->
            item(key = "nothing") {
                ListState(
                    ListStateKind.Empty,
                    stringResource(R.string.nothing_found),
                    body = stringResource(R.string.search_nothing_body_format, state.query.trim()),
                )
            }

        state.rows.isEmpty() && !state.searched ->
            item(key = "prompt") {
                ListState(
                    ListStateKind.Empty,
                    stringResource(R.string.search_prompt_title),
                    body = stringResource(R.string.search_prompt_body),
                )
            }
    }
}

// Onest ships 400, 500 and 600 only: a bolder weight would be synthesised.
private fun snippetText(spans: List<SnippetSpan>): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            if (span.bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
            } else {
                append(span.text)
            }
        }
    }
