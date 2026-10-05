package io.github.nytka_app.ui.people

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.UnnamedVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PeopleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakePeople : PeopleClient {
        var people: ApiResult<List<Person>> = ApiResult.Ok(emptyList())
        var voices: ApiResult<List<UnnamedVoice>> = ApiResult.Ok(emptyList())
        var rename: ApiResult<Unit> = ApiResult.Ok(Unit)
        var name: ApiResult<Unit> = ApiResult.Ok(Unit)
        var merge: ApiResult<Unit> = ApiResult.Ok(Unit)
        var delete: ApiResult<Unit> = ApiResult.Ok(Unit)
        var forget: ApiResult<Boolean> = ApiResult.Ok(true)
        var listCalls = 0
        val renamed = mutableListOf<Pair<String, String>>()
        val named = mutableListOf<Pair<String, String>>()
        val merged = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        val forgotten = mutableListOf<String>()

        override suspend fun people() = people.also { listCalls++ }

        override suspend fun voices() = voices

        override suspend fun renamePerson(
            id: String,
            name: String,
        ) = rename.also { renamed += id to name }

        override suspend fun nameVoice(
            speakerId: String,
            name: String,
        ) = this.name.also { named += speakerId to name }

        override suspend fun mergePerson(
            id: String,
            intoId: String,
        ) = merge.also { merged += id to intoId }

        override suspend fun deletePerson(id: String) = delete.also { deleted += id }

        override suspend fun forgetPerson(id: String) = forget.also { forgotten += id }
    }

    private val api = FakePeople()
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.14.0", 1, features = listOf("people")))
    private val anna = Person("p1", "Anna", voices = listOf("4"), segments = 12)
    private val bea = Person("p2", "bea", segments = 1)
    private val voice = UnnamedVoice("7", "SPEAKER_02", 5, "2026-09-29T08:00:00Z")

    private fun newViewModel() = PeopleViewModel(api, InfoClient { info })

    private val updateNeeded = PeopleNotice.Failed(FailureKind.NotFound, "The server answered.", item = false)

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `loads people sorted by name and the unnamed voices`() {
        api.people = ApiResult.Ok(listOf(bea, anna))
        api.voices = ApiResult.Ok(listOf(voice))

        val state = newViewModel().state.value

        assertEquals(listOf("p1", "p2"), state.people.map { it.id })
        assertEquals(listOf("7"), state.voices.map { it.speakerId })
        assertTrue(!state.loading)
        assertNull(state.error)
    }

    @Test
    fun `an older server says it needs an update`() {
        api.people = failure(FailureKind.NotFound)

        assertEquals(updateNeeded, newViewModel().state.value.error)
    }

    @Test
    fun `a 405 on the voices also says it needs an update`() {
        api.voices = failure(FailureKind.Unsupported)

        assertEquals(
            PeopleNotice.Failed(FailureKind.Unsupported, "The server answered.", item = false),
            newViewModel().state.value.error,
        )
    }

    @Test
    fun `naming a voice sends the trimmed name, closes the dialog and reads the lists again`() {
        api.voices = ApiResult.Ok(listOf(voice))
        val vm = newViewModel()
        vm.openVoice(voice)

        vm.nameVoice(voice, "  Carl ")

        assertEquals(listOf("7" to "Carl"), api.named)
        assertNull(vm.state.value.dialog)
        assertEquals(PeopleNotice.VoiceNamed("Carl"), vm.state.value.note)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `a blank name is not sent`() {
        val vm = newViewModel()

        vm.nameVoice(voice, "   ")

        assertTrue(api.named.isEmpty())
    }

    @Test
    fun `renaming sends the new name`() {
        val vm = newViewModel()
        vm.startRename(anna)

        vm.rename(anna, "Anna B")

        assertEquals(listOf("p1" to "Anna B"), api.renamed)
        assertNull(vm.state.value.dialog)
        assertEquals(PeopleNotice.Renamed("Anna B"), vm.state.value.note)
    }

    @Test
    fun `a rename to a taken name keeps the dialog open with the reason`() {
        api.rename = failure(FailureKind.Conflict)
        val vm = newViewModel()
        vm.startRename(anna)

        vm.rename(anna, "Bea")

        assertEquals(PeopleDialog.Rename(anna, PeopleNotice.NameTaken("Bea")), vm.state.value.dialog)
        assertNull(vm.state.value.note)
    }

    @Test
    fun `renaming to the same name sends nothing`() {
        val vm = newViewModel()
        vm.startRename(anna)

        vm.rename(anna, "Anna")

        assertTrue(api.renamed.isEmpty())
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `merging sends the target and reads the lists again`() {
        api.people = ApiResult.Ok(listOf(anna, bea))
        val vm = newViewModel()

        vm.merge(anna, bea)

        assertEquals(listOf("p1" to "p2"), api.merged)
        assertEquals(PeopleNotice.Merged("Anna", "bea"), vm.state.value.note)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `merging into a person who is gone says so and refreshes`() {
        api.merge = failure(FailureKind.NotFound)
        val vm = newViewModel()

        vm.merge(anna, bea)

        assertEquals(
            PeopleNotice.Failed(FailureKind.NotFound, "The server answered.", item = true),
            vm.state.value.note,
        )
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `deleting keeps the voice model`() {
        val vm = newViewModel()

        vm.delete(anna)

        assertEquals(listOf("p1"), api.deleted)
        assertTrue(api.forgotten.isEmpty())
        assertEquals(PeopleNotice.Deleted("Anna"), vm.state.value.note)
    }

    @Test
    fun `forgetting reports whether the voice model was removed`() {
        val vm = newViewModel()

        vm.forget(anna)
        assertEquals(PeopleNotice.DeletedWithVoiceModel("Anna"), vm.state.value.note)

        api.forget = ApiResult.Ok(false)
        vm.forget(anna)

        assertEquals(listOf("p1", "p1"), api.forgotten)
        assertEquals(PeopleNotice.DeletedVoiceModelStays("Anna"), vm.state.value.note)
    }

    @Test
    fun `a failed delete shows the sentence and closes the dialog`() {
        api.delete = failure(FailureKind.Forbidden)
        val vm = newViewModel()
        vm.startDelete(anna)

        vm.delete(anna)

        assertEquals(
            PeopleNotice.Failed(FailureKind.Forbidden, "The server answered.", item = true),
            vm.state.value.note,
        )
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `without lastSeenAt people sort by name and rows show line counts`() {
        api.people = ApiResult.Ok(listOf(bea, anna))

        val state = newViewModel().state.value

        assertEquals(listOf("p1", "p2"), state.people.map { it.id })
        assertTrue(!state.hasSummaries)
    }

    @Test
    fun `with lastSeenAt the latest comes first, never heard last, ties by name`() {
        val old = Person("p3", "Old", lastSeenAt = "2026-10-01T10:00:00Z", factCount = 2)
        val recent = Person("p4", "Zed", lastSeenAt = "2026-10-05T10:00:00Z", factCount = 0)
        val never = Person("p5", "Aaron", factCount = 1)
        api.people = ApiResult.Ok(listOf(never, old, bea, recent))

        val state = newViewModel().state.value

        assertEquals(listOf("p4", "p3", "p5", "p2"), state.people.map { it.id })
        assertTrue(state.hasSummaries)
    }

    @Test
    fun `a row opens the person page when the server lists people and the tab can show it`() {
        val opened = mutableListOf<String>()
        val vm = newViewModel()

        vm.tap(anna) { opened += it }

        assertEquals(listOf("p1"), opened)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `a row opens the actions dialog on a server without people`() {
        info = ApiResult.Ok(ServerInfo("0.12.0", 1))
        val opened = mutableListOf<String>()
        val vm = newViewModel()

        vm.tap(anna) { opened += it }

        assertTrue(opened.isEmpty())
        assertEquals(PeopleDialog.Actions(anna), vm.state.value.dialog)
    }

    @Test
    fun `a row opens the actions dialog while the tab has no person page`() {
        val vm = newViewModel()

        vm.tap(anna, null)

        assertEquals(PeopleDialog.Actions(anna), vm.state.value.dialog)
    }

    @Test
    fun `an unreadable info answer leaves the actions dialog`() {
        info = failure(FailureKind.Network)
        val vm = newViewModel()

        vm.tap(anna) { error("no page") }

        assertEquals(PeopleDialog.Actions(anna), vm.state.value.dialog)
    }
}
