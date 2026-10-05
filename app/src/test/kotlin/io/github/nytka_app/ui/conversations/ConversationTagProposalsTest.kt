package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.TagNotice
import io.github.nytka_app.ui.tags.TagProposalState
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tags.proposal
import io.github.nytka_app.ui.tasks.FakeTasks
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Proposed tags in the conversation: what shows and what a tap does. */
class ConversationTagProposalsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val tags = FakeTags()

    init {
        api.detail =
            ApiResult.Ok(
                ConversationDetail(
                    id = "c1",
                    startedAt = "2026-09-29T08:00:00Z",
                    endedAt = "2026-09-29T08:10:00Z",
                    status = "closed",
                    segments = emptyList(),
                    tags = listOf("family"),
                ),
            )
        tags.pending =
            ApiResult.Ok(
                listOf(
                    proposal("t1", "work"),
                    proposal("t2", "plan", personId = "p1", personName = "Anna"),
                    proposal("t3", "other", conversationId = "c2"),
                ),
            )
    }

    private fun viewModel(info: InfoClient = fakeInfo(ServerInfo.FEATURE_TAGS, ServerInfo.FEATURE_TAG_SUGGESTIONS)) =
        ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1")),
            api,
            FakeTasks(emptyList()),
            FakeBookmarks(),
            FakeReview(),
            FakeAudio(),
            FakePlayer(),
            clock,
            info,
            tags,
        )

    @Test
    fun `shows the proposals of this conversation with no person`() {
        assertEquals(listOf(TagProposalState("t1", "work")), viewModel().state.value.tagProposals)
    }

    @Test
    fun `nothing is applied until a tap`() {
        viewModel()

        assertTrue(tags.answered.isEmpty())
        assertTrue(tags.writes.isEmpty())
    }

    @Test
    fun `a server without tag-suggestions is not asked`() {
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_TAGS))

        vm.acceptTagProposal("t1")

        assertEquals(0, tags.listed)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertTrue(tags.answered.isEmpty())
    }

    @Test
    fun `an unreadable info asks nothing`() {
        val vm = viewModel(InfoClient { ApiResult.Failure(FailureKind.Network, "No network.") })

        assertEquals(0, tags.listed)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `accept adds the chip and drops the proposal`() {
        val vm = viewModel()

        vm.acceptTagProposal("t1")

        assertEquals(listOf("t1" to true), tags.answered)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertNull(vm.state.value.tagNotice)
    }

    @Test
    fun `reject drops the proposal and leaves the chips`() {
        val vm = viewModel()

        vm.rejectTagProposal("t1")

        assertEquals(listOf("t1" to false), tags.answered)
        assertEquals(listOf("family"), vm.state.value.tags)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `the proposal is busy while its answer is on its way`() {
        val gate = CompletableDeferred<Unit>()
        tags.suggestionGate = gate
        val vm = viewModel()

        vm.acceptTagProposal("t1")
        assertEquals(listOf(TagProposalState("t1", "work", busy = true)), vm.state.value.tagProposals)
        vm.rejectTagProposal("t1")
        assertEquals(1, tags.answered.size)

        gate.complete(Unit)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `a 409 that leaves it no longer pending drops it and reads the conversation again`() {
        tags.suggestionAnswer = ApiResult.Failure(FailureKind.Conflict, "x")
        api.detail = ApiResult.Ok((api.detail as ApiResult.Ok).value.copy(tags = listOf("family", "work")))
        val vm = viewModel()

        vm.acceptTagProposal("t1")

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertNull(vm.state.value.tagNotice)
    }

    @Test
    fun `a 404 drops it`() {
        tags.suggestionAnswer = ApiResult.Failure(FailureKind.NotFound, "x")
        val vm = viewModel()

        vm.rejectTagProposal("t1")

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertNull(vm.state.value.tagNotice)
    }

    @Test
    fun `a 409 on accept for a full item keeps the proposal and says so`() {
        tags.suggestionAnswer = ApiResult.Failure(FailureKind.Conflict, "x")
        tags.stayPending = true
        val vm = viewModel()

        vm.acceptTagProposal("t1")

        assertEquals(listOf(TagProposalState("t1", "work")), vm.state.value.tagProposals)
        assertEquals(TagNotice.TooManyTags, vm.state.value.tagNotice)
        assertEquals(listOf("family"), vm.state.value.tags)
    }

    @Test
    fun `a failure keeps the proposal with a typed notice`() {
        tags.suggestionAnswer = ApiResult.Failure(FailureKind.Forbidden, "x")
        val vm = viewModel()

        vm.acceptTagProposal("t1")

        assertEquals(listOf(TagProposalState("t1", "work")), vm.state.value.tagProposals)
        assertEquals(TagNotice.NeedsAdmin, vm.state.value.tagNotice)
    }

    @Test
    fun `a failure of the list hides the row silently`() {
        tags.pending = ApiResult.Failure(FailureKind.Network, "No network.")
        val vm = viewModel()

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertNull(vm.state.value.tagNotice)
        assertNull(vm.state.value.error)
    }
}
