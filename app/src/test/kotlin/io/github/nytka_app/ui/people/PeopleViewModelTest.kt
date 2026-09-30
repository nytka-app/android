package io.github.nytka_app.ui.people

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.NEEDS_UPDATE
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
    private val anna = Person("p1", "Anna", voices = listOf("4"), segments = 12)
    private val bea = Person("p2", "bea", segments = 1)
    private val voice = UnnamedVoice("7", "SPEAKER_02", 5, "2026-09-29T08:00:00Z")

    private fun newViewModel() = PeopleViewModel(api)

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

        assertEquals(NEEDS_UPDATE, newViewModel().state.value.error)
    }

    @Test
    fun `a 405 on the voices also says it needs an update`() {
        api.voices = failure(FailureKind.Unsupported)

        assertEquals(NEEDS_UPDATE, newViewModel().state.value.error)
    }

    @Test
    fun `naming a voice sends the trimmed name, closes the dialog and reads the lists again`() {
        api.voices = ApiResult.Ok(listOf(voice))
        val vm = newViewModel()
        vm.openVoice(voice)

        vm.nameVoice(voice, "  Carl ")

        assertEquals(listOf("7" to "Carl"), api.named)
        assertNull(vm.state.value.dialog)
        assertEquals("Named the voice Carl.", vm.state.value.note)
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
        assertEquals("Renamed to Anna B.", vm.state.value.note)
    }

    @Test
    fun `a rename to a taken name keeps the dialog open with the reason`() {
        api.rename = failure(FailureKind.Conflict)
        val vm = newViewModel()
        vm.startRename(anna)

        vm.rename(anna, "Bea")

        assertEquals(PeopleDialog.Rename(anna, "Someone is already called Bea."), vm.state.value.dialog)
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
        assertEquals("Merged Anna into bea.", vm.state.value.note)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `merging into a person who is gone says so and refreshes`() {
        api.merge = failure(FailureKind.NotFound)
        val vm = newViewModel()

        vm.merge(anna, bea)

        assertEquals("This item no longer exists", vm.state.value.note)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `deleting keeps the voice model`() {
        val vm = newViewModel()

        vm.delete(anna)

        assertEquals(listOf("p1"), api.deleted)
        assertTrue(api.forgotten.isEmpty())
        assertEquals("Deleted Anna.", vm.state.value.note)
    }

    @Test
    fun `forgetting reports whether the voice model was removed`() {
        val vm = newViewModel()

        vm.forget(anna)
        assertEquals("Deleted Anna and their voice model.", vm.state.value.note)

        api.forget = ApiResult.Ok(false)
        vm.forget(anna)

        assertEquals(listOf("p1", "p1"), api.forgotten)
        assertEquals("Deleted Anna, but the voice model could not be removed.", vm.state.value.note)
    }

    @Test
    fun `a failed delete shows the sentence and closes the dialog`() {
        api.delete = failure(FailureKind.Forbidden)
        val vm = newViewModel()
        vm.startDelete(anna)

        vm.delete(anna)

        assertEquals("The app needs an admin token.", vm.state.value.note)
        assertNull(vm.state.value.dialog)
    }
}
