package io.github.nytka_app.ui.people.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.CardAnswer
import io.github.nytka_app.core.api.CardsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.VoiceCard
import io.github.nytka_app.ui.people.PeopleViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a card or its clip came to; the screen words it. Never carries the server's own text except a `400`'s. */
sealed interface CardNotice {
    /** The clip's audio is deleted; the card list is read again. */
    data object ClipGone : CardNotice

    data object ClipFailed : CardNotice

    /** [item] is a call on one listed card, where a 404 means it is gone and not that the server is old. */
    data class Failed(
        val kind: FailureKind,
        val message: String,
        val item: Boolean,
    ) : CardNotice
}

data class CardsUiState(
    /** `/info` lists `voice-groups`; without it the section does not exist. */
    val available: Boolean = false,
    val cards: List<VoiceCard> = emptyList(),
    val loading: Boolean = false,
    /** [clipKey] of the card being answered, so its buttons wait. */
    val busy: String? = null,
    val notice: CardNotice? = null,
)

@HiltViewModel
class CardsViewModel
    @Inject
    constructor(
        private val api: CardsClient,
        private val info: InfoClient,
        private val player: ClipPlayer,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(CardsUiState())
        val state: StateFlow<CardsUiState> = mutableState.asStateFlow()
        val clip: StateFlow<ClipState> = player.state

        init {
            viewModelScope.launch { player.state.collect(::onClip) }
            refresh()
        }

        /** Reads `/info` first: a server without `voice-groups` is never asked for cards. */
        fun refresh() {
            mutableState.update { it.copy(loading = true) }
            viewModelScope.launch {
                val on = (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_VOICE_GROUPS) == true
                if (!on) return@launch hide()
                when (val result = api.cards()) {
                    is ApiResult.Ok -> show(result.value)
                    is ApiResult.Failure -> {
                        hide()
                        if (result.kind != FailureKind.NotFound && result.kind != FailureKind.Unsupported) {
                            mutableState.update { it.copy(notice = result.asNotice(item = false)) }
                        }
                    }
                }
            }
        }

        private fun hide() {
            player.stop()
            mutableState.update { it.copy(available = false, cards = emptyList(), loading = false) }
        }

        private fun show(cards: List<VoiceCard>) {
            val playing = player.state.value.key
            if (playing != null && cards.none { clipKey(it.kind, it.id) == playing }) player.stop()
            mutableState.update { it.copy(available = true, cards = cards, loading = false) }
        }

        /** Plays the card's clip, or stops it when it already plays; another card's clip stops first. */
        fun toggleClip(card: VoiceCard) {
            val key = clipKey(card.kind, card.id)
            val current = player.state.value
            when {
                current.key == key && current.failure == null -> player.stop()
                else -> {
                    if (current.key != null) player.stop()
                    player.play(card.kind, card.id)
                }
            }
        }

        /** The cards left the screen. */
        fun stopClip() = player.stop()

        private fun onClip(clip: ClipState) {
            val failure = clip.failure ?: return
            player.stop()
            mutableState.update {
                it.copy(notice = if (failure == ClipFailure.Gone) CardNotice.ClipGone else CardNotice.ClipFailed)
            }
            if (failure == ClipFailure.Gone) refresh()
        }

        /**
         * Names a group: a typed name that matches a person of [people] sends their id, any other sends the name
         * (the server reuses a person of that name). Only a tap on Save calls this.
         */
        fun save(
            card: VoiceCard,
            name: String,
            people: List<Person>,
            onAnswered: () -> Unit,
        ) {
            val trimmed = name.trim().take(PeopleViewModel.MAX_NAME)
            if (trimmed.isEmpty()) return
            val known = people.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            answer(card, if (known != null) CardAnswer.Person(known.id) else CardAnswer.Name(trimmed), onAnswered)
        }

        /** "Yes" on a match card: confirms the card's own person. */
        fun confirm(
            card: VoiceCard,
            onAnswered: () -> Unit,
        ) {
            val personId = card.personId ?: return
            answer(card, CardAnswer.Person(personId), onAnswered)
        }

        fun skip(
            card: VoiceCard,
            onAnswered: () -> Unit,
        ) = answer(card, CardAnswer.Skip, onAnswered)

        /** "Not a person" on a group, "Not them" on a match. */
        fun reject(
            card: VoiceCard,
            onAnswered: () -> Unit,
        ) = answer(card, CardAnswer.Reject, onAnswered)

        private fun answer(
            card: VoiceCard,
            answer: CardAnswer,
            onAnswered: () -> Unit,
        ) {
            val key = clipKey(card.kind, card.id)
            if (state.value.busy != null) return
            if (player.state.value.key == key) player.stop()
            mutableState.update { it.copy(busy = key) }
            viewModelScope.launch {
                when (val result = api.answer(card, answer)) {
                    is ApiResult.Ok -> {
                        mutableState.update { it.copy(busy = null) }
                        refresh()
                        onAnswered()
                    }

                    is ApiResult.Failure -> {
                        mutableState.update { it.copy(busy = null, notice = result.asNotice(item = true)) }
                        if (result.kind == FailureKind.NotFound) refresh()
                    }
                }
            }
        }

        fun noticeShown() = mutableState.update { it.copy(notice = null) }

        private fun ApiResult.Failure.asNotice(item: Boolean) = CardNotice.Failed(kind, message, item)

        override fun onCleared() {
            player.release()
        }
    }
