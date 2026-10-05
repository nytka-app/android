package io.github.nytka_app.ui.people.review

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NameSuggestion
import io.github.nytka_app.core.api.ReviewClient
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.core.api.ReviewProposal
import io.github.nytka_app.core.api.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeReview : ReviewClient {
        var list: ApiResult<List<ReviewItem>> = ApiResult.Ok(emptyList())
        var answer: ApiResult<Unit> = ApiResult.Ok(Unit)
        var listCalls = 0
        val answers = mutableListOf<Triple<String, String, Boolean>>()

        override suspend fun review(limit: Int) = list.also { listCalls++ }

        override suspend fun answer(
            kind: String,
            id: String,
            accept: Boolean,
        ) = answer.also { answers += Triple(kind, id, accept) }

        override suspend fun suggestions(): ApiResult<List<NameSuggestion>> = ApiResult.Ok(emptyList())

        override suspend fun answerSuggestion(
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = ApiResult.Ok(Unit)
    }

    private val api = FakeReview()
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.15.0", 1, features = listOf("review")))
    private val name =
        ReviewItem("name", "g1", "c1", "Lunch", "2026-10-05T10:00:00Z", "I am Olena", ReviewProposal(name = "Olena"))
    private val voice =
        ReviewItem(
            "voice",
            "g2",
            "c2",
            null,
            "2026-10-04T10:00:00Z",
            "Hi",
            ReviewProposal(name = "Olena", similarity = 0.874),
        )
    private val label =
        ReviewItem("label", "42", "c3", "Call", "2026-10-03T10:00:00Z", "Yes", ReviewProposal(isUser = true))

    private fun newViewModel() = ReviewViewModel(api, InfoClient { info })

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `each kind maps to its row`() {
        assertEquals(ReviewRow.NameSuggestion("Olena"), ReviewRow.of(name))
        assertEquals(ReviewRow.VoiceMatch("Olena", 87), ReviewRow.of(voice))
        assertEquals(ReviewRow.Label(isUser = true), ReviewRow.of(label))
        assertEquals(
            ReviewRow.Label(isUser = false),
            ReviewRow.of(label.copy(proposal = ReviewProposal(isUser = false))),
        )
        assertNull(ReviewRow.of(name.copy(kind = "other")))
    }

    @Test
    fun `lists the items, counts them and skips a kind it does not know`() {
        api.list = ApiResult.Ok(listOf(name, voice, label, name.copy(kind = "other", id = "x")))
        val state = newViewModel().state.value
        assertEquals(listOf(name, voice, label), state.items)
        assertEquals(3, state.count)
        assertTrue(state.available)
        assertNull(state.error)
    }

    @Test
    fun `accept calls the answer with the kind and id and drops the row`() {
        api.list = ApiResult.Ok(listOf(name, label))
        val viewModel = newViewModel()
        viewModel.accept(label)
        assertEquals(listOf(Triple("label", "42", true)), api.answers)
        assertEquals(listOf(name), viewModel.state.value.items)
    }

    @Test
    fun `reject calls the answer and drops the row`() {
        api.list = ApiResult.Ok(listOf(name, voice))
        val viewModel = newViewModel()
        viewModel.reject(name)
        assertEquals(listOf(Triple("name", "g1", false)), api.answers)
        assertEquals(listOf(voice), viewModel.state.value.items)
    }

    @Test
    fun `a refused answer puts the row back where it was with a notice`() {
        api.list = ApiResult.Ok(listOf(name, voice, label))
        api.answer = failure(FailureKind.Forbidden)
        val viewModel = newViewModel()
        viewModel.accept(voice)
        assertEquals(listOf(name, voice, label), viewModel.state.value.items)
        assertEquals(
            ReviewNotice.Failed(FailureKind.Forbidden, "The server answered.", item = false),
            viewModel.state.value.note,
        )
    }

    @Test
    fun `a conflict drops the row and reads the list again`() {
        api.list = ApiResult.Ok(listOf(name, voice))
        val viewModel = newViewModel()
        api.answer = failure(FailureKind.Conflict)
        api.list = ApiResult.Ok(listOf(voice))
        viewModel.accept(name)
        assertEquals(2, api.listCalls)
        assertEquals(listOf(voice), viewModel.state.value.items)
        assertEquals(ReviewNotice.AlreadyAnswered, viewModel.state.value.note)
    }

    @Test
    fun `a 404 on an answer says the item is gone and reads the list again`() {
        api.list = ApiResult.Ok(listOf(name))
        val viewModel = newViewModel()
        api.answer = failure(FailureKind.NotFound)
        api.list = ApiResult.Ok(emptyList())
        viewModel.reject(name)
        assertEquals(2, api.listCalls)
        assertTrue(
            viewModel.state.value.items
                .isEmpty(),
        )
        assertEquals(
            ReviewNotice.Failed(FailureKind.NotFound, "The server answered.", item = true),
            viewModel.state.value.note,
        )
    }

    @Test
    fun `a 404 on the list hides the icon and says the server needs an update`() {
        api.list = failure(FailureKind.NotFound)
        val state = newViewModel().state.value
        assertFalse(state.available)
        assertEquals(ReviewNotice.Failed(FailureKind.NotFound, "The server answered.", item = false), state.error)
    }

    @Test
    fun `a server that does not list review is not asked for the list`() {
        info = ApiResult.Ok(ServerInfo("0.14.0", 1, features = listOf("people")))
        val state = newViewModel().state.value
        assertEquals(0, api.listCalls)
        assertFalse(state.available)
        assertEquals(FailureKind.NotFound, (state.error as ReviewNotice.Failed).kind)
    }

    @Test
    fun `an empty inbox is available with a count of zero`() {
        val state = newViewModel().state.value
        assertTrue(state.available)
        assertEquals(0, state.count)
    }

    @Test
    fun `a failed read after a good one keeps the icon`() {
        api.list = ApiResult.Ok(listOf(name))
        val viewModel = newViewModel()
        api.list = failure(FailureKind.Network)
        viewModel.refresh()
        assertTrue(viewModel.state.value.available)
        assertEquals(listOf(name), viewModel.state.value.items)
    }
}
