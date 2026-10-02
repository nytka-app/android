package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

@Serializable
data class LastDelivery(
    val status: String,
    val at: String,
)

@Serializable
data class Webhook(
    val id: String,
    val url: String,
    val events: List<String>,
    val description: String? = null,
    val active: Boolean = true,
    val createdAt: String,
    val lastDelivery: LastDelivery? = null,
)

/** The answer to creating a webhook: the only time the server shows [secret]. */
@Serializable
data class CreatedWebhook(
    val id: String,
    val url: String,
    val events: List<String>,
    val description: String? = null,
    val active: Boolean = true,
    val createdAt: String,
    val lastDelivery: LastDelivery? = null,
    val secret: String,
) {
    fun toWebhook() = Webhook(id, url, events, description, active, createdAt, lastDelivery)

    override fun toString() = "CreatedWebhook(id=$id, url=$url, secret=***)"
}

@Serializable
data class WebhookList(
    val items: List<Webhook>,
)

/** [status] is `pending`, `delivered` or `failed`. */
@Serializable
data class Delivery(
    val id: String,
    val eventType: String,
    val status: String,
    val attempts: Int = 0,
    val lastStatusCode: Int? = null,
    val lastError: String? = null,
    val createdAt: String,
    val deliveredAt: String? = null,
)

@Serializable
data class DeliveryList(
    val items: List<Delivery>,
)

/** The webhook endpoints of v0.4 (admin token); a server without them answers 404. */
interface WebhooksClient {
    suspend fun webhooks(): ApiResult<List<Webhook>>

    suspend fun createWebhook(
        url: String,
        events: List<String>,
        description: String?,
    ): ApiResult<CreatedWebhook>

    suspend fun setWebhookActive(
        id: String,
        active: Boolean,
    ): ApiResult<Webhook>

    suspend fun deleteWebhook(id: String): ApiResult<Unit>

    /** Queues a `ping`; the answer is the delivery's id. */
    suspend fun testWebhook(id: String): ApiResult<String>

    suspend fun deliveries(
        id: String,
        limit: Int,
    ): ApiResult<List<Delivery>>
}

@Serializable
private data class CreateBody(
    val url: String,
    val events: List<String>,
    val description: String? = null,
)

@Serializable
private data class ActiveBody(
    val active: Boolean,
)

@Serializable
private data class TestAnswer(
    val deliveryId: String,
)

class WebhooksApi(
    private val api: NytkaApi,
) : WebhooksClient {
    override suspend fun webhooks(): ApiResult<List<Webhook>> =
        api.request("GET", "api/v1/webhooks") { api.json.decodeFromString<WebhookList>(it).items }

    override suspend fun createWebhook(
        url: String,
        events: List<String>,
        description: String?,
    ): ApiResult<CreatedWebhook> =
        api.request("POST", "api/v1/webhooks", body = api.json.encodeToString(CreateBody(url, events, description))) {
            api.json.decodeFromString(it)
        }

    override suspend fun setWebhookActive(
        id: String,
        active: Boolean,
    ): ApiResult<Webhook> =
        api.request("PATCH", "api/v1/webhooks/$id", body = api.json.encodeToString(ActiveBody(active))) {
            api.json.decodeFromString(it)
        }

    override suspend fun deleteWebhook(id: String): ApiResult<Unit> = api.request("DELETE", "api/v1/webhooks/$id") { }

    override suspend fun testWebhook(id: String): ApiResult<String> =
        api.request("POST", "api/v1/webhooks/$id/test") { api.json.decodeFromString<TestAnswer>(it).deliveryId }

    override suspend fun deliveries(
        id: String,
        limit: Int,
    ): ApiResult<List<Delivery>> =
        api.request("GET", "api/v1/webhooks/$id/deliveries", mapOf("limit" to limit.toString())) {
            api.json.decodeFromString<DeliveryList>(it).items
        }
}
