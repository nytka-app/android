package io.github.nytka_app.ui.names

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ForgetResult
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KnownPeopleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Only the list is read; every other call is a test bug. */
    private class FakePeople(
        val answer: ApiResult<List<Person>>,
    ) : PeopleClient {
        var calls = 0

        override suspend fun people() = answer.also { calls++ }

        override suspend fun voices(): ApiResult<List<UnnamedVoice>> = error("unused")

        override suspend fun renamePerson(
            id: String,
            name: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun nameVoice(
            speakerId: String,
            name: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun mergePerson(
            id: String,
            intoId: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun deletePerson(id: String): ApiResult<Unit> = error("unused")

        override suspend fun forgetPerson(id: String): ApiResult<Boolean> = error("unused")

        override suspend fun forgetEverything(id: String): ApiResult<ForgetResult> = error("Not used.")
    }

    @Test
    fun `reads the people once and keeps only those with a name`() {
        val api = FakePeople(ApiResult.Ok(listOf(Person("p1", "Olena"), Person("p2", "Repairman", named = false))))

        val vm = KnownPeopleViewModel(api)

        assertEquals(listOf("Olena"), vm.people.value.map { it.name })
        assertEquals(1, api.calls)
    }

    @Test
    fun `a server without people offers no names`() {
        val vm = KnownPeopleViewModel(FakePeople(ApiResult.Failure(FailureKind.NotFound, "Not found.")))

        assertEquals(emptyList<Person>(), vm.people.value)
    }
}
