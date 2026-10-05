package io.github.nytka_app.ui.people.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ReviewClient
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.people.merged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** What a row asks; the screen words it. A kind this app does not know has no row, so it is never answered blind. */
sealed interface ReviewRow {
    /**
     * A pending name suggestion; [name] is null when the server sent none. [role] is set only where `roles` is
     * listed.
     */
    data class NameSuggestion(
        val name: String?,
        val role: String? = null,
        val named: Boolean = true,
    ) : ReviewRow

    /** A voice match; [percent] is the similarity, when the server sent it. */
    data class VoiceMatch(
        val name: String?,
        val percent: Int?,
    ) : ReviewRow

    /** Nytka thinks this line is yours ([isUser]) or someone else's. */
    data class Label(
        val isUser: Boolean,
    ) : ReviewRow

    /** A proposed tag; [personId] is set for a person's tag and null for a conversation's. */
    data class Tag(
        val tag: String,
        val personId: String?,
    ) : ReviewRow

    companion object {
        fun of(
            item: ReviewItem,
            roles: Boolean = false,
        ): ReviewRow? =
            when (item.kind) {
                ReviewItem.KIND_NAME ->
                    if (roles) {
                        NameSuggestion(item.proposal.name, item.proposal.role, item.proposal.named)
                    } else {
                        NameSuggestion(item.proposal.name)
                    }

                ReviewItem.KIND_VOICE ->
                    VoiceMatch(item.proposal.name, item.proposal.similarity?.let { (it * 100).roundToInt() })

                ReviewItem.KIND_LABEL -> Label(item.proposal.isUser == true)
                ReviewItem.KIND_TAG ->
                    item.proposal.tag
                        ?.takeIf(
                            String::isNotBlank,
                        )?.let { Tag(it, item.proposal.personId) }
                else -> null
            }
    }
}

/** What the last read or answer came to; never carries the server's own text except a failure's fixed sentence. */
sealed interface ReviewNotice {
    /** [item] is a call on one listed item, where a 404 means it is gone and not that the server is old. */
    data class Failed(
        val kind: FailureKind,
        val message: String,
        val item: Boolean,
    ) : ReviewNotice

    /** A `409`: someone answered it already. */
    data object AlreadyAnswered : ReviewNotice

    /** A `409` on accepting a tag the item has no room for: the proposal stays in the list. */
    data object TagLimit : ReviewNotice

    /** The server joined the person known by a role to the person who already had [name]. */
    data class MergedInto(
        val name: String,
    ) : ReviewNotice
}

data class ReviewUiState(
    val items: List<ReviewItem> = emptyList(),
    /** The person's name for each person-tag item, by item id; missing where the server gave none. */
    val tagPeople: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    /** `/info` lists `review` and the list answered: the People screen shows its icon. */
    val available: Boolean = false,
    /** `/info` lists `roles`: name rows may carry one. */
    val roles: Boolean = false,
    val error: ReviewNotice? = null,
    /** The result of the last answer, for a snackbar. */
    val note: ReviewNotice? = null,
) {
    val count: Int get() = items.size
}

@HiltViewModel
class ReviewViewModel
    @Inject
    constructor(
        private val api: ReviewClient,
        private val info: InfoClient,
        private val tags: TagsClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ReviewUiState())
        val state: StateFlow<ReviewUiState> = mutableState.asStateFlow()

        /** Items whose answer is on its way: a read in between must not bring them back. */
        private val pending = mutableSetOf<String>()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            mutableState.update { it.copy(loading = true, error = null) }
            val features =
                when (val result = info.info()) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failure -> return unavailable(result)
                }
            if (!features.has(ServerInfo.FEATURE_REVIEW)) {
                return unavailable(ApiResult.Failure(FailureKind.NotFound, ""))
            }
            // A tag item is shown only by a server that lists `tag-suggestions`, and then no more is asked of it.
            val tagsOn = features.has(ServerInfo.FEATURE_TAG_SUGGESTIONS)
            when (val result = api.review(LIMIT)) {
                is ApiResult.Ok -> {
                    val items =
                        result.value.filter { item ->
                            ReviewRow.of(item) != null &&
                                (tagsOn || item.kind != ReviewItem.KIND_TAG) &&
                                key(item) !in pending
                        }
                    val people = if (tagsOn) personNames(items) else emptyMap()
                    mutableState.update {
                        it.copy(
                            items = items,
                            tagPeople = people,
                            loading = false,
                            available = true,
                            roles = features.has(ServerInfo.FEATURE_ROLES),
                        )
                    }
                }

                is ApiResult.Failure -> unavailable(result)
            }
        }

        /**
         * The inbox row of a person's tag names the person, which the review list does not carry but the list of
         * proposals does. Asked only when such a row exists; a failure leaves the row without a name.
         */
        private suspend fun personNames(items: List<ReviewItem>): Map<String, String> {
            if (items.none { it.kind == ReviewItem.KIND_TAG && it.proposal.personId != null }) return emptyMap()
            return (tags.suggestions() as? ApiResult.Ok)
                ?.value
                .orEmpty()
                .mapNotNull { s -> s.personName?.takeIf(String::isNotBlank)?.let { s.id to it } }
                .toMap()
        }

        /** A route the server lacks hides the icon; any other failure keeps what the screen had. */
        private fun unavailable(failure: ApiResult.Failure) {
            val missing = failure.kind == FailureKind.NotFound || failure.kind == FailureKind.Unsupported
            mutableState.update {
                it.copy(
                    loading = false,
                    available = it.available && !missing,
                    error = ReviewNotice.Failed(failure.kind, failure.message, item = false),
                )
            }
        }

        fun accept(item: ReviewItem) = answer(item, accept = true)

        fun reject(item: ReviewItem) = answer(item, accept = false)

        fun noteShown() = mutableState.update { it.copy(note = null) }

        /** Only a tap gets here. The row goes at once; a refusal puts it back where it was. */
        private fun answer(
            item: ReviewItem,
            accept: Boolean,
        ) {
            val index = state.value.items.indexOf(item)
            if (index < 0) return
            pending += key(item)
            mutableState.update { it.copy(items = it.items - item) }
            viewModelScope.launch {
                val result = if (accept) api.acceptReview(item.kind, item.id) else api.answer(item.kind, item.id, false)
                pending -= key(item)
                if (result !is ApiResult.Failure) {
                    val person = (result as? ApiResult.Ok)?.value as? String
                    val name = item.proposal.name
                    if (name != null && merged(item.proposal.personId, person)) {
                        mutableState.update { it.copy(note = ReviewNotice.MergedInto(name)) }
                    }
                    return@launch
                }
                when (result.kind) {
                    FailureKind.Conflict ->
                        if (accept &&
                            item.kind == ReviewItem.KIND_TAG
                        ) {
                            tagConflict(item)
                        } else {
                            gone(ReviewNotice.AlreadyAnswered)
                        }

                    FailureKind.NotFound -> gone(ReviewNotice.Failed(result.kind, result.message, item = true))
                    else ->
                        mutableState.update {
                            val items = it.items.toMutableList()
                            items.add(index.coerceAtMost(items.size), item)
                            it.copy(
                                items = items,
                                note = ReviewNotice.Failed(result.kind, result.message, item = false),
                            )
                        }
                }
            }
        }

        private suspend fun gone(note: ReviewNotice) {
            mutableState.update { it.copy(note = note) }
            load()
        }

        /**
         * Accepting a tag answers `409` both for "answered already" and for "the item has 20 tags". The list read again
         * tells which: a proposal still pending stays, with the limit as the notice.
         */
        private suspend fun tagConflict(item: ReviewItem) {
            load()
            val stays = state.value.items.any { key(it) == key(item) }
            mutableState.update { it.copy(note = if (stays) ReviewNotice.TagLimit else ReviewNotice.AlreadyAnswered) }
        }

        private fun key(item: ReviewItem) = item.kind + "/" + item.id

        companion object {
            const val LIMIT = 200
        }
    }
