package io.github.nytka_app.ui.people.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.VoiceCard
import io.github.nytka_app.ui.DecisionCard
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.names.NameChips
import io.github.nytka_app.ui.names.filled
import io.github.nytka_app.ui.names.nameSuggestions
import io.github.nytka_app.ui.people.PeopleViewModel

private const val MAX_LINES_SHOWN = 3

/** Shows the cards' notices and stops the clip when the list leaves the screen or the app goes to the background. */
@Composable
fun CardsEffects(
    viewModel: CardsViewModel,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopClip() }
    DisposableEffect(Unit) { onDispose { viewModel.stopClip() } }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(noticeText(context, it))
            viewModel.noticeShown()
        }
    }
}

/** The "Who is this?" cards at the top of the People list. Adds nothing while the server has none. */
fun LazyListScope.cardsSection(
    cards: List<VoiceCard>,
    clip: ClipState,
    busy: String?,
    people: List<Person>,
    viewModel: CardsViewModel,
    onAnswered: () -> Unit,
) {
    items(cards.size, key = { "c" + clipKey(cards[it].kind, cards[it].id) }) { index ->
        val card = cards[index]
        val key = clipKey(card.kind, card.id)
        CardItem(
            card = card,
            playing = clip.key == key && clip.playing,
            enabled = busy == null,
            people = people,
            onClip = { viewModel.toggleClip(card) },
            onSave = { viewModel.save(card, it, people, onAnswered) },
            onYes = { viewModel.confirm(card, onAnswered) },
            onSkip = { viewModel.skip(card, onAnswered) },
            onReject = { viewModel.reject(card, onAnswered) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardItem(
    card: VoiceCard,
    playing: Boolean,
    enabled: Boolean,
    people: List<Person>,
    onClip: () -> Unit,
    onSave: (String) -> Unit,
    onYes: () -> Unit,
    onSkip: () -> Unit,
    onReject: () -> Unit,
) {
    val match = card.kind == VoiceCard.KIND_MATCH
    var field by rememberSaveable(card.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(filled("")) }
    val name = field.text
    DecisionCard(
        if (match) {
            stringResource(R.string.cards_match_title_format, card.personName.orEmpty())
        } else {
            stringResource(R.string.cards_group_title)
        },
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        card.conversationTitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.cards_heard_in_format, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        card.lines.take(MAX_LINES_SHOWN).forEach {
            Text(stringResource(R.string.source_quote, it.text), style = MaterialTheme.typography.bodyMedium)
        }
        if (card.clip != null) {
            FilledTonalButton(onClick = onClip, enabled = enabled) {
                Icon(
                    if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (playing) R.string.cards_stop_clip else R.string.cards_play_clip))
            }
        }
        if (!match) {
            NytkaTextField(
                value = field,
                onValueChange = { field = it.copy(text = it.text.take(PeopleViewModel.MAX_NAME)) },
                label = stringResource(R.string.people_name_label),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            NameChips(nameSuggestions(people, name), onPick = { field = filled(it) }, enabled = enabled)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (match) {
                Button(onClick = onYes, enabled = enabled) { Text(stringResource(R.string.cards_yes)) }
            } else {
                Button(onClick = { onSave(name) }, enabled = enabled && name.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            }
            OutlinedButton(onClick = onReject, enabled = enabled) {
                Text(stringResource(if (match) R.string.cards_not_them else R.string.cards_not_person))
            }
            TextButton(onClick = onSkip, enabled = enabled) { Text(stringResource(R.string.cards_skip)) }
        }
    }
}
