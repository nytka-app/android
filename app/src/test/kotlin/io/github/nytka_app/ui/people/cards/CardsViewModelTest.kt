package io.github.nytka_app.ui.people.cards

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioRequest
import io.github.nytka_app.core.api.CardAnswer
import io.github.nytka_app.core.api.CardClip
import io.github.nytka_app.core.api.CardsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.VoiceCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeCards : CardsClient {
        var cards: ApiResult<List<VoiceCard>> = ApiResult.Ok(emptyList())
        var answer: ApiResult<Unit> = ApiResult.Ok(Unit)
        var listCalls = 0
        val answers = mutableListOf<Pair<String, CardAnswer>>()

        override suspend fun cards() = cards.also { listCalls++ }

        override suspend fun answer(
            card: VoiceCard,
            answer: CardAnswer,
        ) = this.answer.also { answers += card.id to answer }

        override suspend fun clipRequest(
            kind: String,
            id: String,
        ): AudioRequest? = null
    }

    private class FakeClipPlayer : ClipPlayer {
        val mutable = MutableStateFlow(ClipState())
        override val state: StateFlow<ClipState> = mutable
        val calls = mutableListOf<String>()
        var released = false

        override fun play(
            kind: String,
            id: String,
        ) {
            calls += "play $kind/$id"
            mutable.value = ClipState(clipKey(kind, id), playing = true)
        }

        override fun stop() {
            calls += "stop"
            mutable.value = ClipState()
        }

        override fun release() {
            released = true
        }

        fun fail(failure: ClipFailure) {
            mutable.value = mutable.value.copy(playing = false, failure = failure)
        }
    }

    private val api = FakeCards()
    private val player = FakeClipPlayer()
    private var info: ApiResult<ServerInfo> =
        ApiResult.Ok(ServerInfo("0.14.0", 1, features = listOf("people", "voice-groups")))
    private val group = VoiceCard("group", "g1", "c1", "Lunch", clip = CardClip("a", "b"))
    private val other = VoiceCard("group", "g2", "c2", "Walk", clip = CardClip("a", "b"))
    private val match = VoiceCard("match", "m1", "c1", "Lunch", personId = "p1", personName = "Anna", similarity = 0.8)
    private val anna = Person("p1", "Anna")
    private var answered = 0

    private fun newViewModel() = CardsViewModel(api, InfoClient { info }, player)

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `a server without voice-groups is never asked for cards`() {
        info = ApiResult.Ok(ServerInfo("0.14.0", 1, features = listOf("people")))

        val state = newViewModel().state.value

        assertEquals(0, api.listCalls)
        assertFalse(state.available)
        assertTrue(state.cards.isEmpty())
    }

    @Test
    fun `an unreadable info hides the section`() {
        info = failure(FailureKind.Network)

        assertFalse(newViewModel().state.value.available)
        assertEquals(0, api.listCalls)
    }

    @Test
    fun `loads the cards when the feature is listed`() {
        api.cards = ApiResult.Ok(listOf(group, match))

        val state = newViewModel().state.value

        assertTrue(state.available)
        assertEquals(listOf("g1", "m1"), state.cards.map { it.id })
    }

    @Test
    fun `a server that lacks the route hides the section without a notice`() {
        api.cards = failure(FailureKind.NotFound)

        val state = newViewModel().state.value

        assertFalse(state.available)
        assertNull(state.notice)
    }

    @Test
    fun `a forbidden token hides the section and says so`() {
        api.cards = failure(FailureKind.Forbidden)

        val state = newViewModel().state.value

        assertFalse(state.available)
        assertEquals(CardNotice.Failed(FailureKind.Forbidden, "The server answered.", item = false), state.notice)
    }

    @Test
    fun `a group named like an existing person sends that person`() {
        api.cards = ApiResult.Ok(listOf(group))

        newViewModel().save(group, " anna ", listOf(anna)) { answered++ }

        assertEquals(listOf("g1" to CardAnswer.Person("p1")), api.answers)
        assertEquals(1, answered)
    }

    @Test
    fun `a group given a new name sends the name`() {
        api.cards = ApiResult.Ok(listOf(group))

        newViewModel().save(group, "Olena", listOf(anna)) { answered++ }

        assertEquals(listOf("g1" to CardAnswer.Name("Olena")), api.answers)
    }

    @Test
    fun `a blank name sends nothing`() {
        newViewModel().save(group, "  ", listOf(anna)) { answered++ }

        assertTrue(api.answers.isEmpty())
        assertEquals(0, answered)
    }

    @Test
    fun `yes on a match sends the card's person`() {
        newViewModel().confirm(match) { answered++ }

        assertEquals(listOf("m1" to CardAnswer.Person("p1")), api.answers)
    }

    @Test
    fun `skip and reject send their answers`() {
        val model = newViewModel()

        model.skip(group) { answered++ }
        model.reject(match) { answered++ }

        assertEquals(listOf("g1" to CardAnswer.Skip, "m1" to CardAnswer.Reject), api.answers)
        assertEquals(2, answered)
    }

    @Test
    fun `after an answer the cards are read again`() {
        api.cards = ApiResult.Ok(listOf(group))
        val model = newViewModel()
        api.cards = ApiResult.Ok(emptyList())

        model.skip(group) { answered++ }

        assertEquals(2, api.listCalls)
        assertTrue(
            model.state.value.cards
                .isEmpty(),
        )
        assertEquals(1, answered)
    }

    @Test
    fun `a refused answer keeps the cards, says why and does not call back`() {
        api.cards = ApiResult.Ok(listOf(group))
        api.answer = failure(FailureKind.Forbidden)
        val model = newViewModel()

        model.skip(group) { answered++ }

        assertEquals(
            CardNotice.Failed(FailureKind.Forbidden, "The server answered.", item = true),
            model.state.value.notice,
        )
        assertEquals(0, answered)
        assertEquals(1, model.state.value.cards.size)
        assertNull(model.state.value.busy)
    }

    @Test
    fun `a card that is gone on answering reads the list again`() {
        api.cards = ApiResult.Ok(listOf(group))
        api.answer = failure(FailureKind.NotFound)
        val model = newViewModel()

        model.skip(group) { answered++ }

        assertEquals(2, api.listCalls)
        assertEquals(
            CardNotice.Failed(FailureKind.NotFound, "The server answered.", item = true),
            model.state.value.notice,
        )
    }

    @Test
    fun `a clip that is gone says so and reads the cards again`() {
        api.cards = ApiResult.Ok(listOf(group))
        val model = newViewModel()
        model.toggleClip(group)

        player.fail(ClipFailure.Gone)

        assertEquals(CardNotice.ClipGone, model.state.value.notice)
        assertEquals(2, api.listCalls)
        assertNull(player.state.value.key)
    }

    @Test
    fun `a clip that fails otherwise says so and keeps the cards`() {
        api.cards = ApiResult.Ok(listOf(group))
        val model = newViewModel()
        model.toggleClip(group)

        player.fail(ClipFailure.Other)

        assertEquals(CardNotice.ClipFailed, model.state.value.notice)
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `playing a second card stops the first`() {
        api.cards = ApiResult.Ok(listOf(group, other))
        val model = newViewModel()

        model.toggleClip(group)
        model.toggleClip(other)

        assertEquals(listOf("play group/g1", "stop", "play group/g2"), player.calls)
        assertEquals(clipKey("group", "g2"), player.state.value.key)
    }

    @Test
    fun `tapping the playing card stops it`() {
        val model = newViewModel()

        model.toggleClip(group)
        model.toggleClip(group)

        assertEquals(listOf("play group/g1", "stop"), player.calls)
    }

    @Test
    fun `answering a card stops its clip`() {
        val model = newViewModel()
        model.toggleClip(group)

        model.skip(group) { }

        assertNull(player.state.value.key)
    }

    @Test
    fun `a clip whose card left the list stops`() {
        api.cards = ApiResult.Ok(listOf(group))
        val model = newViewModel()
        model.toggleClip(group)
        api.cards = ApiResult.Ok(listOf(other))

        model.refresh()

        assertNull(player.state.value.key)
    }

    @Test
    fun `clearing the view model releases the player`() {
        val model = newViewModel()

        model.javaClass
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(model)

        assertTrue(player.released)
    }
}
