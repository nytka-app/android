package io.github.nytka_app.ui.people

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.VoiceprintsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceModelsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Fake : VoiceprintsClient {
        var calls = 0
        var answer: ApiResult<Unit> = ApiResult.Ok(Unit)

        override suspend fun deleteAll(): ApiResult<Unit> {
            calls++
            return answer
        }
    }

    private val client = Fake()

    private fun viewModel() = VoiceModelsViewModel(client)

    @Test
    fun `asking opens the confirm dialog and deletes nothing`() {
        val viewModel = viewModel()

        viewModel.ask()

        assertTrue(viewModel.state.value.confirming)
        assertEquals(0, client.calls)
    }

    @Test
    fun `cancelling deletes nothing`() {
        val viewModel = viewModel()
        viewModel.ask()

        viewModel.cancel()

        assertFalse(viewModel.state.value.confirming)
        assertEquals(0, client.calls)
    }

    @Test
    fun `confirming deletes once and reports it`() {
        val viewModel = viewModel()
        viewModel.ask()

        viewModel.confirm()

        assertEquals(1, client.calls)
        assertEquals(VoiceModelsNotice.Deleted, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.confirming)
        assertFalse(viewModel.state.value.deleting)
    }

    @Test
    fun `a read token and an older server are reported by kind`() {
        client.answer = ApiResult.Failure(FailureKind.Forbidden, "The token is not allowed to do this.")
        val viewModel = viewModel()

        viewModel.confirm()

        assertEquals(
            VoiceModelsNotice.Failed(FailureKind.Forbidden, "The token is not allowed to do this."),
            viewModel.state.value.notice,
        )

        viewModel.noticeShown()
        assertNull(viewModel.state.value.notice)
    }
}
