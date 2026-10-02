package io.github.nytka_app.ui.developer.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Delivery
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Webhook
import io.github.nytka_app.core.api.WebhooksClient
import io.github.nytka_app.ui.itemNotice
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The secret of a webhook just created: shown once, then gone. */
data class NewSecret(
    val url: String,
    val secret: String,
) {
    override fun toString() = "NewSecret(url=$url, secret=***)"
}

/** The add dialog. [error] is what the server or the URL check refused. */
data class WebhookCreator(
    val saving: Boolean = false,
    val error: String? = null,
)

data class WebhooksUiState(
    val webhooks: List<Webhook> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val creator: WebhookCreator? = null,
    val secret: NewSecret? = null,
    /** The webhook whose screen is open. */
    val selected: Webhook? = null,
    val deliveries: List<Delivery> = emptyList(),
    val deliveriesLoading: Boolean = false,
    val note: String? = null,
)

@HiltViewModel
class WebhooksViewModel
    @Inject
    constructor(
        private val api: WebhooksClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(WebhooksUiState())
        val state: StateFlow<WebhooksUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = api.webhooks()) {
                    is ApiResult.Ok ->
                        mutableState.update { current ->
                            current.copy(
                                webhooks = result.value,
                                loading = false,
                                selected =
                                    current.selected?.let { open ->
                                        result.value.firstOrNull { it.id == open.id }
                                    },
                            )
                        }

                    is ApiResult.Failure -> mutableState.update { it.copy(loading = false, error = result.notice()) }
                }
            }
        }

        fun startAdd() = mutableState.update { it.copy(creator = WebhookCreator()) }

        fun dismissCreator() = mutableState.update { it.copy(creator = null) }

        fun dismissSecret() = mutableState.update { it.copy(secret = null) }

        /** [events] holds event names, or `*` alone for every event. */
        fun create(
            url: String,
            events: List<String>,
            description: String,
        ) {
            val trimmed = url.trim()
            val problem = urlProblem(trimmed) ?: if (events.isEmpty()) "Pick at least one event." else null
            if (problem != null) {
                mutableState.update { it.copy(creator = WebhookCreator(error = problem)) }
                return
            }
            mutableState.update { it.copy(creator = WebhookCreator(saving = true)) }
            viewModelScope.launch {
                when (val result = api.createWebhook(trimmed, events, description.trim().ifEmpty { null })) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                webhooks = it.webhooks + result.value.toWebhook(),
                                creator = null,
                                secret = NewSecret(result.value.url, result.value.secret),
                            )
                        }

                    is ApiResult.Failure ->
                        mutableState.update { it.copy(creator = WebhookCreator(error = creatorError(result))) }
                }
            }
        }

        fun setActive(
            id: String,
            active: Boolean,
        ) {
            viewModelScope.launch {
                when (val result = api.setWebhookActive(id, active)) {
                    is ApiResult.Ok -> replace(result.value)
                    is ApiResult.Failure -> itemFailed(result) { copy(error = it) }
                }
            }
        }

        fun open(id: String) {
            val webhook = mutableState.value.webhooks.firstOrNull { it.id == id } ?: return
            mutableState.update { it.copy(selected = webhook, deliveries = emptyList(), note = null) }
            loadDeliveries(id)
        }

        fun close() = mutableState.update { it.copy(selected = null, deliveries = emptyList(), note = null) }

        fun loadDeliveries(id: String) {
            mutableState.update { it.copy(deliveriesLoading = true) }
            viewModelScope.launch {
                when (val result = api.deliveries(id, DELIVERIES)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            if (it.selected?.id ==
                                id
                            ) {
                                it.copy(deliveries = result.value, deliveriesLoading = false)
                            } else {
                                it
                            }
                        }

                    is ApiResult.Failure ->
                        itemFailed(result) { copy(deliveriesLoading = false, note = it) }
                }
            }
        }

        fun sendTest() {
            val id = mutableState.value.selected?.id ?: return
            viewModelScope.launch {
                when (val result = api.testWebhook(id)) {
                    is ApiResult.Ok -> {
                        mutableState.update { it.copy(note = "Test sent. It shows below once delivered.") }
                        loadDeliveries(id)
                    }

                    is ApiResult.Failure -> itemFailed(result) { copy(note = it) }
                }
            }
        }

        fun delete() {
            val id = mutableState.value.selected?.id ?: return
            viewModelScope.launch {
                val result = api.deleteWebhook(id)
                if (result is ApiResult.Ok || (result as? ApiResult.Failure)?.kind == FailureKind.NotFound) {
                    mutableState.update {
                        it.copy(
                            webhooks =
                                it.webhooks.filter { hook ->
                                    hook.id != id
                                },
                            selected = null,
                            deliveries = emptyList(),
                        )
                    }
                } else if (result is ApiResult.Failure) {
                    mutableState.update { it.copy(note = result.notice()) }
                }
            }
        }

        /** A call on one webhook failed: a 404 means it is gone, so the list is read again. */
        private fun itemFailed(
            failure: ApiResult.Failure,
            show: WebhooksUiState.(String) -> WebhooksUiState,
        ) {
            mutableState.update { it.show(failure.itemNotice()) }
            if (failure.kind == FailureKind.NotFound) refresh()
        }

        private fun replace(webhook: Webhook) =
            mutableState.update { current ->
                current.copy(
                    webhooks = current.webhooks.map { if (it.id == webhook.id) webhook else it },
                    selected = if (current.selected?.id == webhook.id) webhook else current.selected,
                )
            }

        private fun creatorError(failure: ApiResult.Failure): String =
            when (failure.kind) {
                FailureKind.Conflict -> "The server allows at most $MAX_WEBHOOKS webhooks."
                FailureKind.Invalid ->
                    failure.errors.values
                        .flatten()
                        .firstOrNull() ?: failure.message

                else -> failure.notice()
            }

        companion object {
            const val MAX_WEBHOOKS = 20
            private const val DELIVERIES = 30
            private const val MAX_URL = 2048

            /** The events a webhook can ask for, with the wording the screen uses. */
            val EVENTS =
                listOf(
                    "conversation.ready" to "A conversation is summarized",
                    "task.created" to "A task is created",
                    "task.completed" to "A task is completed",
                    "memory.created" to "A memory is created",
                )

            /** Null when [url] is an http or https URL the server accepts. */
            fun urlProblem(url: String): String? =
                when {
                    url.length > MAX_URL -> "The URL is too long."
                    !(url.startsWith("https://") || url.startsWith("http://")) || url.substringAfter("://").isEmpty() ->
                        "The URL must start with http:// or https://."

                    else -> null
                }
        }
    }
