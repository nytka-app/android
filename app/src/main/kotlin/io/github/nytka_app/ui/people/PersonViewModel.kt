package io.github.nytka_app.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NoteChange
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.PersonConversation
import io.github.nytka_app.core.api.PersonFact
import io.github.nytka_app.core.api.PersonPage
import io.github.nytka_app.core.api.PersonPageClient
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.tags.TagAccess
import io.github.nytka_app.ui.tags.TagNotice
import io.github.nytka_app.ui.tags.tagAccess
import io.github.nytka_app.ui.tags.tagNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the last read or action came to; the screen words it. Never carries the server's own text but a `400`'s. */
sealed interface PersonNotice {
    /** The page route is missing: the server is older than 0.14 (the person is still listed). */
    data object NeedsUpdate : PersonNotice

    /** The person is no longer listed. */
    data object Gone : PersonNotice

    data object FactExists : PersonNotice

    data object NoteSaved : PersonNotice

    data class NameTaken(
        val name: String,
    ) : PersonNotice

    data class Renamed(
        val name: String,
    ) : PersonNotice

    data class Merged(
        val from: String,
        val into: String,
    ) : PersonNotice

    data class Deleted(
        val name: String,
    ) : PersonNotice

    data class DeletedWithVoiceModel(
        val name: String,
    ) : PersonNotice

    data class DeletedVoiceModelStays(
        val name: String,
    ) : PersonNotice

    /** [item] is a call on one fact or this person, where a 404 means it is gone and not that the server is old. */
    data class Failed(
        val kind: FailureKind,
        val message: String,
        val item: Boolean,
    ) : PersonNotice
}

/** The one dialog open on the page. [error] is what the server refused, shown in the field. */
sealed interface PersonDialog {
    data class AddFact(
        val error: PersonNotice? = null,
    ) : PersonDialog

    data class EditFact(
        val fact: PersonFact,
        val error: PersonNotice? = null,
    ) : PersonDialog

    data class DeleteFact(
        val fact: PersonFact,
    ) : PersonDialog

    data class Rename(
        val error: PersonNotice? = null,
    ) : PersonDialog

    data object Merge : PersonDialog

    data object Delete : PersonDialog
}

/** The part of the page above the facts. [voiceprintSamples] is all the phone learns of the voice model. */
data class PersonHeader(
    val name: String,
    val lastSeenAt: String?,
    val hasVoiceprint: Boolean,
    val voiceprintSamples: Int,
)

data class PersonUiState(
    val header: PersonHeader? = null,
    /** The note as stored; [noteDraft] is what the field holds. */
    val note: String = "",
    val noteDraft: String = "",
    val facts: List<PersonFact> = emptyList(),
    /** Null when the last page of facts is in. */
    val nextBefore: String? = null,
    val loadingMore: Boolean = false,
    val tasks: List<NytkaTask> = emptyList(),
    val conversations: List<PersonConversation> = emptyList(),
    /** The people a merge can go into, read when the merge dialog opens; null until then. */
    val others: List<Person>? = null,
    val loading: Boolean = true,
    val error: PersonNotice? = null,
    val dialog: PersonDialog? = null,
    /** The result of the last action, for a snackbar. */
    val notice: PersonNotice? = null,
    /** The person was deleted or merged away: the screen leaves. */
    val gone: Boolean = false,
    /** The person's tags, as the server holds them; the screen shows them only when [tagAccess] allows. */
    val tags: List<String> = emptyList(),
    val tagAccess: TagAccess = TagAccess.None,
    val tagNotice: TagNotice? = null,
)

@HiltViewModel
class PersonViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val pages: PersonPageClient,
        private val people: PeopleClient,
        private val info: InfoClient,
        private val tagsClient: TagsClient,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"]) { "The person route carries an id." }
        private val mutableState = MutableStateFlow(PersonUiState())
        val state: StateFlow<PersonUiState> = mutableState.asStateFlow()

        init {
            refresh()
            viewModelScope.launch { loadTagAccess() }
        }

        fun refresh() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = pages.person(id)) {
                    is ApiResult.Ok -> mutableState.update { render(it, result.value) }
                    is ApiResult.Failure -> {
                        val error = readError(result)
                        mutableState.update { it.copy(loading = false, error = error) }
                    }
                }
            }
        }

        /**
         * A 404 on the page with the person still listed means a server without person pages; with the person
         * gone from the list it means they were deleted elsewhere.
         */
        private suspend fun readError(failure: ApiResult.Failure): PersonNotice {
            if (failure.kind != FailureKind.NotFound) return failed(failure, item = false)
            return when (val list = people.people()) {
                is ApiResult.Ok -> if (list.value.any { it.id == id }) PersonNotice.NeedsUpdate else PersonNotice.Gone
                is ApiResult.Failure -> failed(list, item = false)
            }
        }

        private fun render(
            current: PersonUiState,
            page: PersonPage,
        ) = current.copy(
            header = PersonHeader(page.name, page.lastSeenAt, page.hasVoiceprint, page.voiceprintSamples),
            note = page.note.orEmpty(),
            noteDraft = page.note.orEmpty(),
            facts = page.facts,
            nextBefore = if (page.facts.size >= PAGE) page.facts.last().id else null,
            tasks = page.openTasks,
            conversations = page.conversations,
            loading = false,
            error = null,
            tags = page.tags,
        )

        fun setNoteDraft(text: String) = mutableState.update { it.copy(noteDraft = text.take(MAX_NOTE)) }

        /** An emptied field clears the note. */
        fun saveNote() {
            val text = state.value.noteDraft.trim()
            if (text == state.value.note) return
            viewModelScope.launch {
                val change = if (text.isEmpty()) NoteChange.Clear else NoteChange.Set(text)
                when (val result = pages.update(id, name = null, note = change)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            val stored = result.value.note.orEmpty()
                            it.copy(note = stored, noteDraft = stored, notice = PersonNotice.NoteSaved)
                        }

                    is ApiResult.Failure -> notify(failed(result, item = true))
                }
            }
        }

        fun loadMoreFacts() {
            val before = state.value.nextBefore ?: return
            if (state.value.loadingMore) return
            mutableState.update { it.copy(loadingMore = true) }
            viewModelScope.launch {
                when (val result = pages.facts(id, before, PAGE)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            val known = it.facts.mapTo(HashSet()) { fact -> fact.id }
                            it.copy(
                                facts = it.facts + result.value.items.filter { fact -> fact.id !in known },
                                nextBefore = result.value.nextBefore,
                                loadingMore = false,
                            )
                        }

                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(loadingMore = false, notice = failed(result, item = false))
                        }
                }
            }
        }

        fun startMerge() {
            mutableState.update { it.copy(dialog = PersonDialog.Merge, others = null) }
            viewModelScope.launch {
                when (val result = people.people()) {
                    is ApiResult.Ok ->
                        mutableState.update { it.copy(others = result.value.filter { person -> person.id != id }) }

                    is ApiResult.Failure -> {
                        mutableState.update { it.copy(dialog = null) }
                        notify(failed(result, item = false))
                    }
                }
            }
        }

        fun dismissDialog() = show(null)

        /** Chips need the `tags` feature, and changing them an admin token; any failure of `/info` shows none. */
        private suspend fun loadTagAccess() {
            val access = (info.info() as? ApiResult.Ok)?.value?.tagAccess() ?: TagAccess.None
            mutableState.update { it.copy(tagAccess = access) }
        }

        /** The server normalizes [name] and answers the person's tags, which replace the chips. */
        fun addTag(name: String) {
            val wanted = name.trim()
            if (wanted.isEmpty() || state.value.tagAccess != TagAccess.Edit) return
            viewModelScope.launch {
                when (val result = tagsClient.addPersonTag(id, wanted)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(tags = result.value) }
                    is ApiResult.Failure -> mutableState.update { it.copy(tagNotice = result.tagNotice()) }
                }
            }
        }

        /** The chip goes at once and comes back, with a notice, if the server refuses. */
        fun removeTag(name: String) {
            if (state.value.tagAccess != TagAccess.Edit) return
            val before = state.value.tags
            mutableState.update { it.copy(tags = it.tags - name) }
            viewModelScope.launch {
                when (val result = tagsClient.removePersonTag(id, name)) {
                    is ApiResult.Ok -> mutableState.update { it.copy(tags = result.value) }
                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                tags = before,
                                tagNotice = result.tagNotice(),
                            )
                        }
                }
            }
        }

        fun tagNoticeShown() = mutableState.update { it.copy(tagNotice = null) }

        fun noticeShown() = mutableState.update { it.copy(notice = null) }

        fun addFact(text: String) {
            val trimmed = text.trim().take(MAX_FACT)
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                when (val result = pages.addFact(id, trimmed)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                facts =
                                    listOf(result.value) +
                                        it.facts.filter { f ->
                                            f.id != result.value.id
                                        },
                                dialog = null,
                            )
                        }

                    is ApiResult.Failure -> dialogError(result, PersonDialog::AddFact)
                }
            }
        }

        fun editFact(
            fact: PersonFact,
            text: String,
        ) {
            val trimmed = text.trim().take(MAX_FACT)
            if (trimmed.isEmpty()) return
            if (trimmed == fact.text) return dismissDialog()
            viewModelScope.launch {
                when (val result = pages.editFact(id, fact.id, trimmed)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                facts =
                                    it.facts.map { f ->
                                        if (f.id ==
                                            fact.id
                                        ) {
                                            result.value
                                        } else {
                                            f
                                        }
                                    },
                                dialog = null,
                            )
                        }

                    is ApiResult.Failure -> dialogError(result) { error -> PersonDialog.EditFact(fact, error) }
                }
            }
        }

        /** The row goes at once and comes back, in its place, when the server refuses. */
        fun deleteFact(fact: PersonFact) {
            val before = state.value.facts
            val index = before.indexOfFirst { it.id == fact.id }
            if (index < 0) return dismissDialog()
            mutableState.update { it.copy(facts = it.facts.filter { f -> f.id != fact.id }, dialog = null) }
            viewModelScope.launch {
                val result = pages.deleteFact(id, fact.id)
                if (result is ApiResult.Failure && result.kind != FailureKind.NotFound) {
                    mutableState.update {
                        val restored = it.facts.toMutableList().apply { add(index.coerceAtMost(size), fact) }
                        it.copy(facts = restored, notice = failed(result, item = true))
                    }
                }
            }
        }

        fun rename(name: String) {
            val trimmed = name.trim().take(MAX_NAME)
            val current = state.value.header?.name
            if (trimmed.isEmpty() || trimmed == current) return dismissDialog()
            viewModelScope.launch {
                when (val result = pages.update(id, name = trimmed, note = NoteChange.Keep)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                header = it.header?.copy(name = result.value.name),
                                dialog = null,
                                notice = PersonNotice.Renamed(result.value.name),
                            )
                        }

                    is ApiResult.Failure ->
                        if (result.kind == FailureKind.Conflict) {
                            mutableState.update {
                                it.copy(
                                    dialog = PersonDialog.Rename(PersonNotice.NameTaken(trimmed)),
                                )
                            }
                        } else {
                            mutableState.update { it.copy(dialog = null, notice = failed(result, item = true)) }
                        }
                }
            }
        }

        fun merge(into: Person) {
            val name =
                state.value.header
                    ?.name
                    .orEmpty()
            viewModelScope.launch {
                when (val result = people.mergePerson(id, into.id)) {
                    is ApiResult.Ok -> leave(PersonNotice.Merged(name, into.name))
                    is ApiResult.Failure -> {
                        mutableState.update { it.copy(dialog = null) }
                        notify(failed(result, item = true))
                    }
                }
            }
        }

        /** [forgetVoice] also asks the transcription service to drop the person's voiceprints. */
        fun delete(forgetVoice: Boolean) {
            val name =
                state.value.header
                    ?.name
                    .orEmpty()
            viewModelScope.launch {
                if (forgetVoice) {
                    when (val result = people.forgetPerson(id)) {
                        is ApiResult.Ok ->
                            leave(
                                if (result.value) {
                                    PersonNotice.DeletedWithVoiceModel(name)
                                } else {
                                    PersonNotice.DeletedVoiceModelStays(name)
                                },
                            )

                        is ApiResult.Failure -> deleteFailed(result)
                    }
                } else {
                    when (val result = people.deletePerson(id)) {
                        is ApiResult.Ok -> leave(PersonNotice.Deleted(name))
                        is ApiResult.Failure -> deleteFailed(result)
                    }
                }
            }
        }

        private fun deleteFailed(failure: ApiResult.Failure) {
            mutableState.update { it.copy(dialog = null) }
            notify(failed(failure, item = true))
        }

        private fun leave(notice: PersonNotice) =
            mutableState.update { it.copy(dialog = null, notice = notice, gone = true) }

        fun show(dialog: PersonDialog?) = mutableState.update { it.copy(dialog = dialog) }

        private fun notify(notice: PersonNotice) = mutableState.update { it.copy(notice = notice) }

        private fun dialogError(
            failure: ApiResult.Failure,
            dialog: (PersonNotice) -> PersonDialog,
        ) {
            val error =
                if (failure.kind ==
                    FailureKind.Conflict
                ) {
                    PersonNotice.FactExists
                } else {
                    failed(failure, item = true)
                }
            mutableState.update { it.copy(dialog = dialog(error)) }
        }

        private fun failed(
            failure: ApiResult.Failure,
            item: Boolean,
        ) = PersonNotice.Failed(failure.kind, failure.message, item)

        companion object {
            const val PAGE = 50
            const val MAX_NOTE = 500
            const val MAX_FACT = 300
            const val MAX_NAME = 80
        }
    }
