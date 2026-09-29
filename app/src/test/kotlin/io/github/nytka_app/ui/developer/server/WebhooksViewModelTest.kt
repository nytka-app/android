package io.github.nytka_app.ui.developer.server

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.CreatedWebhook
import io.github.nytka_app.core.api.Delivery
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.LastDelivery
import io.github.nytka_app.core.api.Webhook
import io.github.nytka_app.core.api.WebhooksClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebhooksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeWebhooks : WebhooksClient {
        var list: ApiResult<List<Webhook>> = ApiResult.Ok(emptyList())
        var create: ApiResult<CreatedWebhook>? = null
        var deliveries: List<Delivery> = emptyList()
        val created = mutableListOf<Triple<String, List<String>, String?>>()
        val tested = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val activeCalls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun webhooks() = list

        override suspend fun createWebhook(
            url: String,
            events: List<String>,
            description: String?,
        ): ApiResult<CreatedWebhook> {
            created += Triple(url, events, description)
            return create
                ?: ApiResult.Ok(CreatedWebhook("w2", url, events, description, true, "t", null, "whsec_secret"))
        }

        override suspend fun setWebhookActive(
            id: String,
            active: Boolean,
        ): ApiResult<Webhook> {
            activeCalls += id to active
            return ApiResult.Ok(hook(id).copy(active = active))
        }

        override suspend fun deleteWebhook(id: String): ApiResult<Unit> = ApiResult.Ok(Unit).also { deleted += id }

        override suspend fun testWebhook(id: String): ApiResult<String> = ApiResult.Ok("d1").also { tested += id }

        override suspend fun deliveries(
            id: String,
            limit: Int,
        ) = ApiResult.Ok(deliveries)
    }

    private val api = FakeWebhooks()

    private fun newViewModel() = WebhooksViewModel(api)

    @Test
    fun `lists webhooks`() {
        api.list = ApiResult.Ok(listOf(hook("w1")))

        assertEquals(
            listOf("w1"),
            newViewModel()
                .state.value.webhooks
                .map { it.id },
        )
    }

    @Test
    fun `an older server or a read token gets its own sentence`() {
        api.list = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        assertEquals("This server needs an update", newViewModel().state.value.error)

        api.list = ApiResult.Failure(FailureKind.Forbidden, "no")
        assertEquals("The app needs an admin token.", newViewModel().state.value.error)
    }

    @Test
    fun `creating shows the secret once and adds the webhook`() {
        val viewModel = newViewModel()

        viewModel.startAdd()
        viewModel.create(" https://x.test/h ", listOf("*"), " ")

        assertEquals(Triple("https://x.test/h", listOf("*"), null), api.created.single())
        assertEquals(NewSecret("https://x.test/h", "whsec_secret"), viewModel.state.value.secret)
        assertEquals(
            listOf("w2"),
            viewModel.state.value.webhooks
                .map { it.id },
        )
        assertNull(viewModel.state.value.creator)

        viewModel.dismissSecret()
        assertNull(viewModel.state.value.secret)
    }

    @Test
    fun `a bad URL or no event is refused before the server`() {
        val viewModel = newViewModel()

        viewModel.create("ftp://x", listOf("*"), "")
        assertEquals(
            "The URL must start with http:// or https://.",
            viewModel.state.value.creator
                ?.error,
        )
        viewModel.create("https://x.test", emptyList(), "")
        assertEquals(
            "Pick at least one event.",
            viewModel.state.value.creator
                ?.error,
        )

        assertTrue(api.created.isEmpty())
    }

    @Test
    fun `the twentieth webhook is the last`() {
        api.create = ApiResult.Failure(FailureKind.Conflict, "conflict")
        val viewModel = newViewModel()

        viewModel.create("https://x.test", listOf("*"), "")

        assertEquals(
            "The server allows at most 20 webhooks.",
            viewModel.state.value.creator
                ?.error,
        )
        assertNull(viewModel.state.value.secret)
    }

    @Test
    fun `the switch patches active`() {
        api.list = ApiResult.Ok(listOf(hook("w1")))
        val viewModel = newViewModel()

        viewModel.setActive("w1", false)

        assertEquals(listOf("w1" to false), api.activeCalls)
        assertEquals(
            false,
            viewModel.state.value.webhooks
                .single()
                .active,
        )
    }

    @Test
    fun `a webhook's screen sends a test, lists deliveries and deletes`() {
        api.list = ApiResult.Ok(listOf(hook("w1")))
        api.deliveries = listOf(Delivery("d1", "ping", "failed", 6, 500, "HTTP 500", "t", null))
        val viewModel = newViewModel()

        viewModel.open("w1")
        assertEquals(
            listOf("d1"),
            viewModel.state.value.deliveries
                .map { it.id },
        )

        viewModel.sendTest()
        assertEquals(listOf("w1"), api.tested)

        viewModel.delete()
        assertEquals(listOf("w1"), api.deleted)
        assertTrue(
            viewModel.state.value.webhooks
                .isEmpty(),
        )
        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun `a delivery reads as attempts, outcome and time`() {
        assertEquals(
            "6 attempts · HTTP 500 · t",
            deliveryDetail(Delivery("d", "ping", "failed", 6, 500, null, "t", null)),
        )
        assertEquals(
            "1 attempt · timeout · done",
            deliveryDetail(Delivery("d", "ping", "failed", 1, null, "timeout", "t", "done")),
        )
    }

    private companion object {
        fun hook(id: String) =
            Webhook(id, "https://x.test/$id", listOf("*"), null, true, "t", LastDelivery("delivered", "t"))
    }
}
