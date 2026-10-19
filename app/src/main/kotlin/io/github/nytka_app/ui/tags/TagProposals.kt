package io.github.nytka_app.ui.tags

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.TagSuggestion
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.DecisionCard

/** One proposed tag as the screen shows it. [busy] is true while its answer is on its way. */
data class TagProposalState(
    val id: String,
    val name: String,
    val busy: Boolean = false,
)

/** What an answer to a proposal came to; the view model acts on it. */
sealed interface ProposalOutcome {
    /** The server added [tag] to the item. */
    data class Accepted(
        val tag: String,
    ) : ProposalOutcome

    data object Rejected : ProposalOutcome

    /** A `409` or `404`: the proposal is gone from the list, and the item may have changed, so read it again. */
    data object Dropped : ProposalOutcome

    /** The proposal stays; [notice] says why (the item holds 20 tags, no admin token, a failure). */
    data class Kept(
        val notice: TagNotice,
    ) : ProposalOutcome

    /** Nothing was sent: another answer is on its way, or there is no such proposal. */
    data object Ignored : ProposalOutcome
}

/**
 * The pending proposals that belong to one screen ([belongs] picks them from the server's list, which has no filter
 * and holds at most 200) and the answers to them. A list failure, an older server included, leaves no proposals and
 * no notice. Nothing calls an accept route but [answer], which a tap on a button reaches.
 */
class TagProposalBook(
    private val client: TagsClient,
    private val belongs: (TagSuggestion) -> Boolean,
) {
    private var pending: List<TagSuggestion> = emptyList()
    private var answering: String? = null

    fun views(): List<TagProposalState> = pending.map { TagProposalState(it.id, it.name, busy = it.id == answering) }

    suspend fun load() {
        pending = (client.suggestions() as? ApiResult.Ok)?.value.orEmpty().filter(belongs)
    }

    /** [changed] is called when [views] changed before the call returns (the busy mark). */
    suspend fun answer(
        id: String,
        accept: Boolean,
        changed: () -> Unit,
    ): ProposalOutcome {
        val proposal = pending.firstOrNull { it.id == id }
        if (proposal == null || answering != null) return ProposalOutcome.Ignored
        answering = id
        changed()
        try {
            return when (val result = client.answerSuggestion(id, accept)) {
                is ApiResult.Ok -> {
                    pending = pending.filterNot { it.id == id }
                    if (accept) ProposalOutcome.Accepted(proposal.name) else ProposalOutcome.Rejected
                }

                is ApiResult.Failure -> refused(result, proposal, accept)
            }
        } finally {
            answering = null
            changed()
        }
    }

    /**
     * A `409` on accept is either "no longer pending" or "the item has 20 tags", and both look the same; the list
     * read again tells which: a proposal still pending was refused for the limit and stays.
     */
    private suspend fun refused(
        failure: ApiResult.Failure,
        proposal: TagSuggestion,
        accept: Boolean,
    ): ProposalOutcome {
        if (failure.kind != FailureKind.Conflict && failure.kind != FailureKind.NotFound) {
            return ProposalOutcome.Kept(failure.tagNotice())
        }
        load()
        val stillPending = pending.any { it.id == proposal.id }
        return if (stillPending && accept && failure.kind == FailureKind.Conflict) {
            ProposalOutcome.Kept(TagNotice.TooManyTags)
        } else {
            pending = pending.filterNot { it.id == proposal.id }
            ProposalOutcome.Dropped
        }
    }
}

/** The tags with a proposal's name added, in the order the server keeps them. */
fun List<String>.withTag(tag: String): List<String> = (this + tag).distinct().sorted()

/**
 * "Suggested tags" under a saffron header (it waits for the owner): each proposal has an Add and a Not this button.
 * Nothing is sent until one is tapped.
 */
@Composable
fun TagProposalRow(
    proposals: List<TagProposalState>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (proposals.isEmpty()) return
    DecisionCard(stringResource(R.string.tags_suggested), modifier) {
        proposals.forEach { ProposalLine(it, onAccept, onReject) }
    }
}

@Composable
private fun ProposalLine(
    proposal: TagProposalState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val accept = stringResource(R.string.tag_proposal_accept_format, proposal.name)
    val reject = stringResource(R.string.tag_proposal_reject_format, proposal.name)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(proposal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onReject(proposal.id) },
            enabled = !proposal.busy,
            modifier = Modifier.semantics { contentDescription = reject },
        ) { Text(stringResource(R.string.tag_proposal_reject_label)) }
        FilledTonalButton(
            onClick = { onAccept(proposal.id) },
            enabled = !proposal.busy,
            modifier = Modifier.semantics { contentDescription = accept },
        ) { Text(stringResource(R.string.action_add)) }
    }
}

/** [TagProposalRow] as one list item, when there is a proposal. */
fun LazyListScope.tagProposalsItem(
    proposals: List<TagProposalState>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    if (proposals.isEmpty()) return
    item(key = "tag-proposals") { TagProposalRow(proposals, onAccept, onReject) }
}
