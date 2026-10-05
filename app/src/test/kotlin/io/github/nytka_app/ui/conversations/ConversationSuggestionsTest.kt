package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.conversations.FakeReview.Companion.suggestion
import io.github.nytka_app.ui.people.RoleNotice
import io.github.nytka_app.ui.people.SuggestionWording
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tags.proposal
import io.github.nytka_app.ui.tasks.FakeTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The name-suggestion banner and the person on a transcript line. */
class ConversationSuggestionsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val review = FakeReview()
    private val tags = FakeTags()

    private fun viewModel() =
        ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1")),
            api,
            FakeTasks(emptyList()),
            FakeBookmarks(),
            review,
            FakeAudio(),
            FakePlayer(),
            clock,
            info,
            tags,
        )

    private var info = fakeInfo()

    private fun detail(segments: List<Segment>) =
        ConversationDetail(
            id = "c1",
            startedAt = "2026-09-29T08:00:00Z",
            endedAt = "2026-09-29T08:10:00Z",
            status = "closed",
            segments = segments,
        )

    init {
        api.detail = ApiResult.Ok(detail(emptyList()))
    }

    private fun withSegments(vararg segments: Segment) {
        api.detail = ApiResult.Ok(detail(segments.toList()))
    }

    private val voice = Segment(1, "2026-09-29T08:01:00Z", "2026-09-29T08:01:04Z", "I'm Olena.", "SPEAKER_01", "sp1")

    @Test
    fun `the banner shows only suggestions of this conversation, the most confident first`() {
        withSegments(voice)
        review.pending =
            ApiResult.Ok(
                listOf(
                    suggestion("s1", confidence = 0.6, name = "Olya"),
                    suggestion("s2", confidence = 0.9),
                    suggestion("s3", conversationId = "c2", confidence = 0.99, name = "Ivan"),
                ),
            )

        val banner = viewModel().state.value.banner

        assertEquals("s2", banner?.id)
        assertEquals("Olena", banner?.name)
    }

    @Test
    fun `no suggestion for this conversation means no banner`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1", conversationId = "c2")))

        assertNull(viewModel().state.value.banner)
    }

    @Test
    fun `the banner calls the evidence line by its current label, else nothing`() {
        withSegments(voice)
        review.pending = ApiResult.Ok(listOf(suggestion("s1", segmentId = 1)))
        assertEquals(
            "SPEAKER_01",
            viewModel()
                .state.value.banner
                ?.label,
        )
        assertEquals(
            "I'm Olena.",
            viewModel()
                .state.value.banner
                ?.evidence,
        )

        withSegments(voice.copy(speaker = null))
        assertNull(
            viewModel()
                .state.value.banner
                ?.label,
        )

        withSegments(voice.copy(id = 9))
        assertNull(
            viewModel()
                .state.value.banner
                ?.label,
        )
    }

    @Test
    fun `accepting answers the server then reads the conversation again`() {
        withSegments(voice)
        review.pending = ApiResult.Ok(listOf(suggestion("s1")))
        val model = viewModel()
        api.detail = ApiResult.Ok(detail(listOf(voice.copy(personId = "p1", personName = "Olena"))))

        model.acceptSuggestion()

        assertEquals(listOf("s1" to true), review.answered)
        assertNull(model.state.value.banner)
        assertEquals(
            "Olena",
            model.state.value.paragraphs
                .single()
                .speaker,
        )
        assertEquals(
            "p1",
            model.state.value.paragraphs
                .single()
                .personId,
        )
    }

    @Test
    fun `rejecting hides the banner and leaves the transcript alone`() {
        withSegments(voice)
        review.pending = ApiResult.Ok(listOf(suggestion("s1")))
        val model = viewModel()

        model.rejectSuggestion()

        assertEquals(listOf("s1" to false), review.answered)
        assertNull(model.state.value.banner)
        assertEquals(
            "SPEAKER_01",
            model.state.value.paragraphs
                .single()
                .speaker,
        )
    }

    @Test
    fun `nothing is answered until a tap`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1")))

        viewModel()

        assertTrue(review.answered.isEmpty())
    }

    @Test
    fun `a failure to list hides the banner without a notice`() {
        review.pending = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = viewModel().state.value

        assertNull(state.banner)
        assertNull(state.suggestionNotice)
        assertNull(state.error)
    }

    @Test
    fun `a failed answer keeps the banner and says so`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1")))
        review.answer = ApiResult.Failure(FailureKind.Network, "No connection.")
        val model = viewModel()

        model.acceptSuggestion()

        assertEquals(SuggestionNotice.Failed, model.state.value.suggestionNotice)
        assertEquals(
            "s1",
            model.state.value.banner
                ?.id,
        )
        assertFalse(
            model.state.value.banner!!
                .busy,
        )

        review.answer = ApiResult.Failure(FailureKind.Forbidden, "Forbidden.")
        model.suggestionNoticeShown()
        model.rejectSuggestion()
        assertEquals(SuggestionNotice.NeedsAdmin, model.state.value.suggestionNotice)
    }

    @Test
    fun `a conflict means it was answered already, so the banner goes and the lists are read again`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1")))
        val model = viewModel()
        review.answer = ApiResult.Failure(FailureKind.Conflict, "Conflict.")
        val listed = review.listed

        model.acceptSuggestion()

        assertNull(model.state.value.banner)
        assertNull(model.state.value.suggestionNotice)
        assertEquals(listed + 1, review.listed)
    }

    @Test
    fun `paragraphs of a named run carry the person, the wearer's lines and unnamed voices do not`() {
        withSegments(
            voice.copy(personId = "p1", personName = "Olena"),
            voice.copy(id = 2, personId = "p1", personName = "Olena", isUser = true),
            voice.copy(id = 3, speaker = "SPEAKER_02", speakerId = "sp2"),
        )

        val paragraphs = viewModel().state.value.paragraphs

        assertEquals(listOf<String?>("p1", null, null), paragraphs.map { it.personId })
    }

    private fun withRoles() {
        info = fakeInfo(ServerInfo.FEATURE_ROLES)
    }

    @Test
    fun `a role without a name reads as the role alone`() {
        withRoles()
        withSegments(voice)
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Repairman", role = "repairman", named = false)))

        val banner = viewModel().state.value.banner

        assertEquals(SuggestionWording.RoleOnly("repairman"), banner?.wording)
    }

    @Test
    fun `a name with a role shows both`() {
        withRoles()
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Mykola", role = "repairman")))

        assertEquals(
            SuggestionWording.NameAndRole("Mykola", "repairman"),
            viewModel()
                .state.value.banner
                ?.wording,
        )
    }

    @Test
    fun `neither field, as an older server sends it, reads as the name`() {
        withRoles()
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Olena")))

        assertEquals(
            SuggestionWording.Name("Olena"),
            viewModel()
                .state.value.banner
                ?.wording,
        )
    }

    @Test
    fun `without the roles feature the role is ignored`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Repairman", role = "repairman", named = false)))

        assertEquals(
            SuggestionWording.Name("Repairman"),
            viewModel()
                .state.value.banner
                ?.wording,
        )
    }

    @Test
    fun `accepting a role-only suggestion sends the accept once, on the tap`() {
        withRoles()
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Repairman", role = "repairman", named = false)))
        val vm = viewModel()
        assertTrue(review.answered.isEmpty())

        vm.acceptSuggestion()

        assertEquals(listOf("s1" to true), review.answered)
        assertNull(vm.state.value.roleNotice)
    }

    @Test
    fun `accepting a name that merged the person into another says so`() {
        withRoles()
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Mykola", personId = "p-role")))
        review.acceptedPerson = "p-mykola"
        val vm = viewModel()

        vm.acceptSuggestion()

        assertEquals(RoleNotice.MergedInto("Mykola"), vm.state.value.roleNotice)
        vm.roleNoticeShown()
        assertNull(vm.state.value.roleNotice)
    }

    @Test
    fun `accepting a name that kept the same person says nothing`() {
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Mykola", personId = "p-role")))
        review.acceptedPerson = "p-role"
        val vm = viewModel()

        vm.acceptSuggestion()

        assertNull(vm.state.value.roleNotice)
    }

    @Test
    fun `a role banner and the suggested tags row show together`() {
        info = fakeInfo(ServerInfo.FEATURE_ROLES, ServerInfo.FEATURE_TAGS, ServerInfo.FEATURE_TAG_SUGGESTIONS)
        review.pending = ApiResult.Ok(listOf(suggestion("s1", name = "Repairman", role = "repairman", named = false)))
        tags.pending = ApiResult.Ok(listOf(proposal("t1", "work")))

        val state = viewModel().state.value

        assertEquals(SuggestionWording.RoleOnly("repairman"), state.banner?.wording)
        assertEquals(listOf("work"), state.tagProposals.map { it.name })
    }
}
