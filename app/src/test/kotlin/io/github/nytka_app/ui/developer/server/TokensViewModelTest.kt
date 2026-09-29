package io.github.nytka_app.ui.developer.server

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.AccessToken
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.CreatedToken
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.TokensClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TokensViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Fake : TokensClient {
        val tokens = mutableListOf(AccessToken("t1", "agent", "read", "ab12", "2026-09-29T08:00:00Z"))
        var listAnswer: ApiResult.Failure? = null
        var createAnswer: ApiResult.Failure? = null
        val revoked = mutableListOf<String>()

        override suspend fun tokens(): ApiResult<List<AccessToken>> = listAnswer ?: ApiResult.Ok(tokens.toList())

        override suspend fun createToken(
            name: String,
            scope: String,
        ): ApiResult<CreatedToken> {
            createAnswer?.let { return it }
            val created = CreatedToken("t2", name, scope, "nyt_secret", "cret", "2026-09-29T09:00:00Z")
            tokens.add(0, created.listed())
            return ApiResult.Ok(created)
        }

        override suspend fun revokeToken(id: String): ApiResult<Unit> {
            revoked += id
            val index = tokens.indexOfFirst { it.id == id }
            tokens[index] = tokens[index].copy(revokedAt = "2026-09-29T10:00:00Z")
            return ApiResult.Ok(Unit)
        }
    }

    private val api = Fake()

    private fun viewModel() = TokensViewModel(api)

    @Test
    fun `lists the tokens, revoked ones included`() {
        assertEquals(
            listOf("agent"),
            viewModel()
                .state.value.tokens
                .map { it.name },
        )
    }

    @Test
    fun `a created token is shown once and then gone`() {
        val viewModel = viewModel()

        viewModel.create("  laptop ", "read")

        assertEquals(
            "nyt_secret",
            viewModel.state.value.created
                ?.token,
        )
        assertEquals(
            listOf("laptop", "agent"),
            viewModel.state.value.tokens
                .map { it.name },
        )
        viewModel.dismissCreated()
        assertNull(viewModel.state.value.created)
        // The listing never carries the secret.
        assertTrue(
            viewModel.state.value.tokens
                .none { it.toString().contains("nyt_secret") },
        )
    }

    @Test
    fun `a name in use is refused in the dialog`() {
        api.createAnswer = ApiResult.Failure(FailureKind.Conflict, "conflict")
        val viewModel = viewModel()

        viewModel.create("agent", "read")

        assertEquals("A token with this name is in use.", viewModel.state.value.createError)
        assertNull(viewModel.state.value.created)
    }

    @Test
    fun `an empty or overlong name is not sent`() {
        val viewModel = viewModel()

        viewModel.create("   ", "read")
        assertNotNull(viewModel.state.value.createError)
        viewModel.create("x".repeat(65), "read")

        assertEquals(1, viewModel.state.value.tokens.size)
    }

    @Test
    fun `revoking reloads the list`() {
        val viewModel = viewModel()

        viewModel.revoke("t1")

        assertEquals(listOf("t1"), api.revoked)
        assertEquals(
            "2026-09-29T10:00:00Z",
            viewModel.state.value.tokens
                .single()
                .revokedAt,
        )
    }

    @Test
    fun `a v0_1 server says it needs an update`() {
        api.listAnswer = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        assertEquals("This server needs an update.", viewModel().state.value.error)
    }
}
