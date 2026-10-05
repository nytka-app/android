package io.github.nytka_app.ui.people

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FactPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NoteChange
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.PersonFact
import io.github.nytka_app.core.api.PersonPage
import io.github.nytka_app.core.api.PersonPageClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.TagAccess
import io.github.nytka_app.ui.tags.TagNotice
import io.github.nytka_app.ui.tags.TagProposalState
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tags.proposal
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Chips on the person page: what shows, adding and removing. */
class PersonTagsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val pages =
        object : PersonPageClient {
            override suspend fun person(id: String) =
                ApiResult.Ok(PersonPage(id = "p1", name = "Anna", tags = listOf("family", "work")))

            override suspend fun update(
                id: String,
                name: String?,
                note: NoteChange,
            ): ApiResult<Person> = error("Not used.")

            override suspend fun facts(
                id: String,
                before: String?,
                limit: Int,
            ): ApiResult<FactPage> = error("Not used.")

            override suspend fun addFact(
                id: String,
                text: String,
            ): ApiResult<PersonFact> = error("Not used.")

            override suspend fun editFact(
                id: String,
                factId: String,
                text: String,
            ): ApiResult<PersonFact> = error("Not used.")

            override suspend fun deleteFact(
                id: String,
                factId: String,
            ): ApiResult<Unit> = error("Not used.")
        }

    private val people =
        object : PeopleClient {
            override suspend fun people(): ApiResult<List<Person>> = ApiResult.Ok(emptyList())

            override suspend fun voices(): ApiResult<List<UnnamedVoice>> = ApiResult.Ok(emptyList())

            override suspend fun renamePerson(
                id: String,
                name: String,
            ): ApiResult<Unit> = error("Not used.")

            override suspend fun nameVoice(
                speakerId: String,
                name: String,
            ): ApiResult<Unit> = error("Not used.")

            override suspend fun mergePerson(
                id: String,
                intoId: String,
            ): ApiResult<Unit> = error("Not used.")

            override suspend fun deletePerson(id: String): ApiResult<Unit> = error("Not used.")

            override suspend fun forgetPerson(id: String): ApiResult<Boolean> = error("Not used.")
        }

    private val tags = FakeTags()

    private fun viewModel(info: InfoClient = fakeInfo(ServerInfo.FEATURE_TAGS)) =
        PersonViewModel(SavedStateHandle(mapOf("id" to "p1")), pages, people, info, tags)

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `the page's tags show with an admin token`() {
        val state = viewModel().state.value

        assertEquals(listOf("family", "work"), state.tags)
        assertEquals(TagAccess.Edit, state.tagAccess)
    }

    @Test
    fun `a read token shows the chips but changes nothing`() {
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_TAGS, scope = ServerInfo.SCOPE_READ))

        vm.addTag("work")
        vm.removeTag("work")

        assertEquals(TagAccess.Read, vm.state.value.tagAccess)
        assertEquals(0, tags.calls)
    }

    @Test
    fun `a server without tags shows no chips and makes no call`() {
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_PEOPLE))

        vm.addTag("work")
        vm.removeTag("work")

        assertEquals(TagAccess.None, vm.state.value.tagAccess)
        assertEquals(0, tags.calls)
    }

    @Test
    fun `adding replaces the chips with the server's answer`() {
        tags.answer = ApiResult.Ok(listOf("family", "work", "mykola"))
        val vm = viewModel()

        vm.addTag("Mykola")

        assertEquals(listOf("add people p1 Mykola"), tags.writes)
        assertEquals(listOf("family", "work", "mykola"), vm.state.value.tags)
    }

    @Test
    fun `removing drops the chip at once and a refusal brings it back`() {
        val gate = CompletableDeferred<Unit>()
        tags.gate = gate
        tags.answer = failure(FailureKind.Network)
        val vm = viewModel()

        vm.removeTag("family")
        assertEquals(listOf("work"), vm.state.value.tags)

        gate.complete(Unit)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertEquals(TagNotice.Failed("The server answered."), vm.state.value.tagNotice)
        assertEquals(listOf("remove people p1 family"), tags.writes)
    }

    @Test
    fun `the notice says what the server refused`() {
        val vm = viewModel()

        tags.answer = failure(FailureKind.Invalid)
        vm.addTag("a/b")
        assertEquals(TagNotice.InvalidTag, vm.state.value.tagNotice)

        tags.answer = failure(FailureKind.Conflict)
        vm.addTag("twenty-one")
        assertEquals(TagNotice.TooManyTags, vm.state.value.tagNotice)

        tags.answer = failure(FailureKind.Forbidden)
        vm.addTag("work")
        assertEquals(TagNotice.NeedsAdmin, vm.state.value.tagNotice)

        vm.tagNoticeShown()
        assertNull(vm.state.value.tagNotice)
    }

    private val suggesting = arrayOf(ServerInfo.FEATURE_TAGS, ServerInfo.FEATURE_TAG_SUGGESTIONS)

    private fun proposals() {
        tags.pending =
            ApiResult.Ok(
                listOf(
                    proposal("t1", "plumber", conversationId = "c1", personId = "p1", personName = "Anna"),
                    proposal("t2", "work", conversationId = "c1"),
                    proposal("t3", "other", conversationId = "c2", personId = "p2", personName = "Ivan"),
                ),
            )
    }

    @Test
    fun `proposals show for this person only`() {
        proposals()

        assertEquals(
            listOf(TagProposalState("t1", "plumber")),
            viewModel(fakeInfo(*suggesting)).state.value.tagProposals,
        )
    }

    @Test
    fun `a server before person proposals changes nothing`() {
        tags.pending = ApiResult.Ok(listOf(proposal("t2", "work", conversationId = "c1")))
        val vm = viewModel(fakeInfo(*suggesting))

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertNull(vm.state.value.tagNotice)
    }

    @Test
    fun `a server without tag-suggestions is not asked`() {
        proposals()
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_TAGS))

        vm.acceptTagProposal("t1")

        assertEquals(0, tags.listed)
        assertTrue(tags.answered.isEmpty())
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `nothing is applied until a tap, and accept adds the chip`() {
        proposals()
        val vm = viewModel(fakeInfo(*suggesting))
        assertTrue(tags.answered.isEmpty())

        vm.acceptTagProposal("t1")

        assertEquals(listOf("t1" to true), tags.answered)
        assertEquals(listOf("family", "plumber", "work"), vm.state.value.tags)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `reject drops the proposal`() {
        proposals()
        val vm = viewModel(fakeInfo(*suggesting))

        vm.rejectTagProposal("t1")

        assertEquals(listOf("t1" to false), tags.answered)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
    }

    @Test
    fun `a 409 or 404 drops it and reads the page again`() {
        proposals()
        tags.suggestionAnswer = failure(FailureKind.Conflict)
        val vm = viewModel(fakeInfo(*suggesting))

        vm.acceptTagProposal("t1")

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertNull(vm.state.value.tagNotice)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
    }

    @Test
    fun `a full person keeps the proposal and says so`() {
        proposals()
        tags.suggestionAnswer = failure(FailureKind.Conflict)
        tags.stayPending = true
        val vm = viewModel(fakeInfo(*suggesting))

        vm.acceptTagProposal("t1")

        assertEquals(listOf(TagProposalState("t1", "plumber")), vm.state.value.tagProposals)
        assertEquals(TagNotice.TooManyTags, vm.state.value.tagNotice)
    }

    @Test
    fun `a failure keeps the proposal with a notice`() {
        proposals()
        tags.suggestionAnswer = failure(FailureKind.Forbidden)
        val vm = viewModel(fakeInfo(*suggesting))

        vm.acceptTagProposal("t1")

        assertEquals(listOf(TagProposalState("t1", "plumber")), vm.state.value.tagProposals)
        assertEquals(TagNotice.NeedsAdmin, vm.state.value.tagNotice)
    }

    @Test
    fun `a failure of the list hides the row silently`() {
        tags.pending = failure(FailureKind.Network)
        val vm = viewModel(fakeInfo(*suggesting))

        assertTrue(
            vm.state.value.tagProposals
                .isEmpty(),
        )
        assertNull(vm.state.value.tagNotice)
        assertNull(vm.state.value.error)
    }
}
