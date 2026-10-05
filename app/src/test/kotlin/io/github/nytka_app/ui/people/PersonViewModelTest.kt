package io.github.nytka_app.ui.people

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FactPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.NoteChange
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.PersonConversation
import io.github.nytka_app.core.api.PersonFact
import io.github.nytka_app.core.api.PersonPage
import io.github.nytka_app.core.api.PersonPageClient
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.fakeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PersonViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakePages : PersonPageClient {
        var page: ApiResult<PersonPage> = ApiResult.Ok(PersonPage("p1"))
        var update: ApiResult<Person> = ApiResult.Ok(Person("p1", "Anna"))
        var more: ApiResult<FactPage> = ApiResult.Ok(FactPage())
        var add: ApiResult<PersonFact> = ApiResult.Ok(PersonFact("f9", text = "Lives in Lviv"))
        var edit: ApiResult<PersonFact> = ApiResult.Ok(PersonFact("f1", text = "x"))
        var delete: ApiResult<Unit> = ApiResult.Ok(Unit)
        var pageCalls = 0
        val updates = mutableListOf<Triple<String, String?, NoteChange>>()
        val befores = mutableListOf<String?>()
        val added = mutableListOf<String>()
        val edited = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()

        override suspend fun person(id: String) = page.also { pageCalls++ }

        override suspend fun update(
            id: String,
            name: String?,
            note: NoteChange,
        ) = update.also { updates += Triple(id, name, note) }

        override suspend fun facts(
            id: String,
            before: String?,
            limit: Int,
        ) = more.also { befores += before }

        override suspend fun addFact(
            id: String,
            text: String,
        ) = add.also { added += text }

        override suspend fun editFact(
            id: String,
            factId: String,
            text: String,
        ) = edit.also { edited += factId to text }

        override suspend fun deleteFact(
            id: String,
            factId: String,
        ) = delete.also { deleted += factId }
    }

    private class FakePeople : PeopleClient {
        var people: ApiResult<List<Person>> = ApiResult.Ok(emptyList())
        var merge: ApiResult<Unit> = ApiResult.Ok(Unit)
        var delete: ApiResult<Unit> = ApiResult.Ok(Unit)
        var forget: ApiResult<Boolean> = ApiResult.Ok(true)
        val merged = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        val forgotten = mutableListOf<String>()

        override suspend fun people() = people

        override suspend fun voices(): ApiResult<List<UnnamedVoice>> = ApiResult.Ok(emptyList())

        override suspend fun renamePerson(
            id: String,
            name: String,
        ) = ApiResult.Ok(Unit)

        override suspend fun nameVoice(
            speakerId: String,
            name: String,
        ) = ApiResult.Ok(Unit)

        override suspend fun mergePerson(
            id: String,
            intoId: String,
        ) = merge.also { merged += id to intoId }

        override suspend fun deletePerson(id: String) = delete.also { deleted += id }

        override suspend fun forgetPerson(id: String) = forget.also { forgotten += id }
    }

    private val pages = FakePages()
    private val people = FakePeople()
    private val anna = Person("p1", "Anna")
    private val bea = Person("p2", "Bea")

    private fun fact(
        id: String,
        text: String = "Fact $id",
    ) = PersonFact(id, "p1", text, "ai", "said", "c1", "Walk")

    private fun newViewModel() =
        PersonViewModel(SavedStateHandle(mapOf("id" to "p1")), pages, people, fakeInfo(), FakeTags())

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    private fun loaded(
        vararg facts: PersonFact,
        note: String? = null,
    ): PersonViewModel {
        pages.page =
            ApiResult.Ok(
                PersonPage(
                    id = "p1",
                    name = "Anna",
                    note = note,
                    lastSeenAt = "2026-10-04T10:00:00Z",
                    hasVoiceprint = true,
                    voiceprintSamples = 7,
                    conversations = listOf(PersonConversation("c1", "Walk", "2026-10-04T10:00:00Z")),
                    facts = facts.toList(),
                    openTasks = listOf(NytkaTask("t1", "c1", "Send the photos", conversationTitle = "Walk")),
                ),
            )
        return newViewModel()
    }

    @Test
    fun `the page maps every field`() {
        val state = loaded(fact("f2"), fact("f1"), note = "Met at the climbing gym").state.value

        assertEquals(PersonHeader("Anna", "2026-10-04T10:00:00Z", true, 7), state.header)
        assertEquals("Met at the climbing gym", state.note)
        assertEquals("Met at the climbing gym", state.noteDraft)
        assertEquals(listOf("f2", "f1"), state.facts.map { it.id })
        assertNull(state.nextBefore)
        assertEquals(listOf("t1"), state.tasks.map { it.id })
        assertEquals(listOf("c1"), state.conversations.map { it.id })
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `a full first page of facts can load more and the next page is appended`() {
        val first = (50 downTo 1).map { fact("f$it") }
        val vm = loaded(*first.toTypedArray())
        assertEquals("f1", vm.state.value.nextBefore)
        pages.more = ApiResult.Ok(FactPage(listOf(fact("f0")), nextBefore = null))

        vm.loadMoreFacts()

        assertEquals(listOf<String?>("f1"), pages.befores)
        assertEquals(
            "f0",
            vm.state.value.facts
                .last()
                .id,
        )
        assertEquals(51, vm.state.value.facts.size)
        assertNull(vm.state.value.nextBefore)
    }

    @Test
    fun `saving an emptied note sends Clear and a text sends Set`() {
        val vm = loaded(note = "Old")
        vm.setNoteDraft("   ")
        pages.update = ApiResult.Ok(Person("p1", "Anna", note = null))

        vm.saveNote()

        assertEquals(NoteChange.Clear, pages.updates.single().third)
        assertNull(pages.updates.single().second)
        assertEquals("", vm.state.value.note)
        assertEquals(PersonNotice.NoteSaved, vm.state.value.notice)

        vm.setNoteDraft(" Runs on Sundays ")
        pages.update = ApiResult.Ok(Person("p1", "Anna", note = "Runs on Sundays"))
        vm.saveNote()

        assertEquals(NoteChange.Set("Runs on Sundays"), pages.updates.last().third)
        assertEquals("Runs on Sundays", vm.state.value.note)
    }

    @Test
    fun `an unchanged note sends nothing and a failed save keeps the draft`() {
        val vm = loaded(note = "Old")
        vm.saveNote()
        assertTrue(pages.updates.isEmpty())

        vm.setNoteDraft("New")
        pages.update = failure(FailureKind.Network)
        vm.saveNote()

        assertEquals("New", vm.state.value.noteDraft)
        assertEquals("Old", vm.state.value.note)
        assertTrue(vm.state.value.notice is PersonNotice.Failed)
    }

    @Test
    fun `the note draft stops at 500 characters`() {
        val vm = loaded()
        vm.setNoteDraft("a".repeat(600))
        assertEquals(500, vm.state.value.noteDraft.length)
    }

    @Test
    fun `adding a fact puts it first and a 409 says they already have it`() {
        val vm = loaded(fact("f1"))
        vm.show(PersonDialog.AddFact())

        vm.addFact("  Lives in Lviv ")

        assertEquals(listOf("Lives in Lviv"), pages.added)
        assertEquals(
            listOf("f9", "f1"),
            vm.state.value.facts
                .map { it.id },
        )
        assertNull(vm.state.value.dialog)

        pages.add = failure(FailureKind.Conflict)
        vm.show(PersonDialog.AddFact())
        vm.addFact("Lives in Lviv")

        assertEquals(PersonDialog.AddFact(PersonNotice.FactExists), vm.state.value.dialog)
        assertEquals(2, vm.state.value.facts.size)
    }

    @Test
    fun `editing a fact replaces its row and a failure stays in the dialog`() {
        val vm = loaded(fact("f1"))
        pages.edit = ApiResult.Ok(fact("f1", "Changed"))

        vm.editFact(
            vm.state.value.facts
                .single(),
            "Changed",
        )

        assertEquals(
            "Changed",
            vm.state.value.facts
                .single()
                .text,
        )

        pages.edit = failure(FailureKind.Conflict)
        vm.editFact(
            vm.state.value.facts
                .single(),
            "Other",
        )

        assertEquals(PersonNotice.FactExists, (vm.state.value.dialog as PersonDialog.EditFact).error)
        assertEquals(
            "Changed",
            vm.state.value.facts
                .single()
                .text,
        )
    }

    @Test
    fun `a refused fact delete puts the row back where it was`() {
        pages.delete = failure(FailureKind.Network)
        val vm = loaded(fact("f3"), fact("f2"), fact("f1"))

        vm.deleteFact(vm.state.value.facts[1])

        assertEquals(
            listOf("f3", "f2", "f1"),
            vm.state.value.facts
                .map { it.id },
        )
        assertTrue(vm.state.value.notice is PersonNotice.Failed)
    }

    @Test
    fun `a delete the server accepted removes the row`() {
        val vm = loaded(fact("f2"), fact("f1"))

        vm.deleteFact(
            vm.state.value.facts
                .first(),
        )

        assertEquals(
            listOf("f1"),
            vm.state.value.facts
                .map { it.id },
        )
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `a 404 on load with the person still listed is NeedsUpdate`() {
        pages.page = failure(FailureKind.NotFound)
        people.people = ApiResult.Ok(listOf(anna))

        assertEquals(PersonNotice.NeedsUpdate, newViewModel().state.value.error)
    }

    @Test
    fun `a 404 on load with the person gone from the list is Gone`() {
        pages.page = failure(FailureKind.NotFound)
        people.people = ApiResult.Ok(listOf(bea))

        assertEquals(PersonNotice.Gone, newViewModel().state.value.error)
    }

    @Test
    fun `another failure on load is shown as it is and can be retried`() {
        pages.page = failure(FailureKind.Forbidden)
        val vm = newViewModel()

        assertEquals(
            PersonNotice.Failed(FailureKind.Forbidden, "The server answered.", item = false),
            vm.state.value.error,
        )

        pages.page = ApiResult.Ok(PersonPage("p1", "Anna"))
        vm.refresh()

        assertNull(vm.state.value.error)
        assertEquals(
            "Anna",
            vm.state.value.header
                ?.name,
        )
    }

    @Test
    fun `rename sends only the name and a 409 says the name is taken`() {
        val vm = loaded()
        pages.update = ApiResult.Ok(Person("p1", "Anya"))

        vm.rename(" Anya ")

        assertEquals(Triple("p1", "Anya", NoteChange.Keep), pages.updates.single())
        assertEquals(
            "Anya",
            vm.state.value.header
                ?.name,
        )
        assertEquals(PersonNotice.Renamed("Anya"), vm.state.value.notice)

        pages.update = failure(FailureKind.Conflict)
        vm.show(PersonDialog.Rename())
        vm.rename("Bea")

        assertEquals(PersonDialog.Rename(PersonNotice.NameTaken("Bea")), vm.state.value.dialog)
        assertEquals(
            "Anya",
            vm.state.value.header
                ?.name,
        )
    }

    @Test
    fun `merge lists the others and merging leaves the page`() {
        people.people = ApiResult.Ok(listOf(anna, bea))
        val vm = loaded()

        vm.startMerge()
        assertEquals(
            listOf("p2"),
            vm.state.value.others
                ?.map { it.id },
        )
        vm.merge(bea)

        assertEquals(listOf("p1" to "p2"), people.merged)
        assertTrue(vm.state.value.gone)
        assertEquals(PersonNotice.Merged("Anna", "Bea"), vm.state.value.notice)
    }

    @Test
    fun `delete leaves the page and keeps the voice model unless asked to forget it`() {
        val vm = loaded()

        vm.delete(forgetVoice = false)

        assertEquals(listOf("p1"), people.deleted)
        assertTrue(people.forgotten.isEmpty())
        assertTrue(vm.state.value.gone)
        assertEquals(PersonNotice.Deleted("Anna"), vm.state.value.notice)
    }

    @Test
    fun `forgetting the voice reports whether the transcription service dropped it`() {
        val vm = loaded()
        vm.delete(forgetVoice = true)
        assertEquals(listOf("p1"), people.forgotten)
        assertEquals(PersonNotice.DeletedWithVoiceModel("Anna"), vm.state.value.notice)

        people.forget = ApiResult.Ok(false)
        val other = loaded()
        other.delete(forgetVoice = true)
        assertEquals(PersonNotice.DeletedVoiceModelStays("Anna"), other.state.value.notice)
    }

    @Test
    fun `a refused delete stays on the page`() {
        people.delete = failure(FailureKind.Forbidden)
        val vm = loaded()

        vm.delete(forgetVoice = false)

        assertFalse(vm.state.value.gone)
        assertTrue(vm.state.value.notice is PersonNotice.Failed)
    }

    @Test
    fun `a person known by a role shows as not named, and naming them marks the header named`() {
        pages.page = ApiResult.Ok(PersonPage("p1", "Repairman", named = false))
        val vm = newViewModel()
        assertEquals(
            false,
            vm.state.value.header
                ?.named,
        )

        pages.update = ApiResult.Ok(Person("p1", "Mykola"))
        vm.rename("Mykola")

        assertEquals(Triple("p1", "Mykola", NoteChange.Keep), pages.updates.single())
        assertEquals(
            "Mykola",
            vm.state.value.header
                ?.name,
        )
        assertEquals(
            true,
            vm.state.value.header
                ?.named,
        )
        assertEquals(PersonNotice.Renamed("Mykola"), vm.state.value.notice)
    }

    @Test
    fun `a named person's header is named`() {
        assertEquals(
            true,
            loaded()
                .state.value.header
                ?.named,
        )
    }
}
