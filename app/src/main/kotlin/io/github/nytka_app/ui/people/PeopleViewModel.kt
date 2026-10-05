package io.github.nytka_app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.conversations.Formatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the last action or read came to; the screen words it. Never carries the server's own text. */
sealed interface PeopleNotice {
    data class Renamed(
        val name: String,
    ) : PeopleNotice

    data class VoiceNamed(
        val name: String,
    ) : PeopleNotice

    data class Merged(
        val from: String,
        val into: String,
    ) : PeopleNotice

    data class Deleted(
        val name: String,
    ) : PeopleNotice

    data class DeletedWithVoiceModel(
        val name: String,
    ) : PeopleNotice

    data class DeletedVoiceModelStays(
        val name: String,
    ) : PeopleNotice

    data class NameTaken(
        val name: String,
    ) : PeopleNotice

    /** [item] is a call on one listed item, where a 404 means it is gone and not that the server is old. */
    data class Failed(
        val kind: FailureKind,
        val message: String,
        val item: Boolean,
    ) : PeopleNotice
}

/** The one dialog open on the People screen. [error] is what the server refused. */
sealed interface PeopleDialog {
    data class Actions(
        val person: Person,
    ) : PeopleDialog

    data class Rename(
        val person: Person,
        val error: PeopleNotice? = null,
    ) : PeopleDialog

    data class Merge(
        val person: Person,
    ) : PeopleDialog

    data class Delete(
        val person: Person,
    ) : PeopleDialog

    data class NameVoice(
        val voice: UnnamedVoice,
        val error: PeopleNotice? = null,
    ) : PeopleDialog
}

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val voices: List<UnnamedVoice> = emptyList(),
    val loading: Boolean = false,
    /** Why the lists could not be read; an older server says it needs an update. */
    val error: PeopleNotice? = null,
    /** `/info` lists `people`: a row opens the person page, otherwise the actions dialog. */
    val personPages: Boolean = false,
    /** The server sent `lastSeenAt` or `facts` for someone, so rows show them instead of the line count. */
    val hasSummaries: Boolean = false,
    val dialog: PeopleDialog? = null,
    /** The result of the last action, for a snackbar. */
    val note: PeopleNotice? = null,
)

@HiltViewModel
class PeopleViewModel
    @Inject
    constructor(
        private val api: PeopleClient,
        private val info: InfoClient,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PeopleUiState())
        val state: StateFlow<PeopleUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val people = api.people()
                val voices = api.voices()
                val personPages = (info.info() as? ApiResult.Ok)?.value?.has(ServerInfo.FEATURE_PEOPLE) == true
                val failure = (people as? ApiResult.Failure) ?: (voices as? ApiResult.Failure)
                mutableState.update { current ->
                    if (failure != null) {
                        current.copy(loading = false, error = failure.asNotice(item = false))
                    } else {
                        val list = (people as ApiResult.Ok).value
                        current.copy(
                            people = sorted(list),
                            voices = (voices as ApiResult.Ok).value,
                            personPages = personPages,
                            hasSummaries = list.any { it.lastSeenAt != null || it.facts != null },
                            loading = false,
                        )
                    }
                }
            }
        }

        /** A row opens the person page when the server has one and the tab can show it, else the actions dialog. */
        fun tap(
            person: Person,
            openPage: ((String) -> Unit)?,
        ) {
            if (state.value.personPages && openPage != null) {
                openPage(person.id)
            } else {
                mutableState.update { it.copy(dialog = PeopleDialog.Actions(person)) }
            }
        }

        fun openVoice(voice: UnnamedVoice) = mutableState.update { it.copy(dialog = PeopleDialog.NameVoice(voice)) }

        fun startRename(person: Person) = mutableState.update { it.copy(dialog = PeopleDialog.Rename(person)) }

        fun startMerge(person: Person) = mutableState.update { it.copy(dialog = PeopleDialog.Merge(person)) }

        fun startDelete(person: Person) = mutableState.update { it.copy(dialog = PeopleDialog.Delete(person)) }

        fun dismissDialog() = mutableState.update { it.copy(dialog = null) }

        fun noteShown() = mutableState.update { it.copy(note = null) }

        fun rename(
            person: Person,
            name: String,
        ) {
            val trimmed = name.trim().take(MAX_NAME)
            if (trimmed.isEmpty() || trimmed == person.name) return dismissDialog()
            viewModelScope.launch {
                when (val result = api.renamePerson(person.id, trimmed)) {
                    is ApiResult.Ok -> done(PeopleNotice.Renamed(trimmed))
                    is ApiResult.Failure ->
                        if (result.kind == FailureKind.Conflict) {
                            mutableState.update {
                                it.copy(dialog = PeopleDialog.Rename(person, PeopleNotice.NameTaken(trimmed)))
                            }
                        } else {
                            failed(result)
                        }
                }
            }
        }

        fun nameVoice(
            voice: UnnamedVoice,
            name: String,
        ) {
            val trimmed = name.trim().take(MAX_NAME)
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                when (val result = api.nameVoice(voice.speakerId, trimmed)) {
                    is ApiResult.Ok -> done(PeopleNotice.VoiceNamed(trimmed))
                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        fun merge(
            person: Person,
            into: Person,
        ) {
            viewModelScope.launch {
                when (val result = api.mergePerson(person.id, into.id)) {
                    is ApiResult.Ok -> done(PeopleNotice.Merged(person.name, into.name))
                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        fun delete(person: Person) {
            viewModelScope.launch {
                when (val result = api.deletePerson(person.id)) {
                    is ApiResult.Ok -> done(PeopleNotice.Deleted(person.name))
                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        /** Deletes the person and asks the transcription service to drop their voiceprints. */
        fun forget(person: Person) {
            viewModelScope.launch {
                when (val result = api.forgetPerson(person.id)) {
                    is ApiResult.Ok ->
                        done(
                            if (result.value) {
                                PeopleNotice.DeletedWithVoiceModel(person.name)
                            } else {
                                PeopleNotice.DeletedVoiceModelStays(person.name)
                            },
                        )

                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        private fun done(note: PeopleNotice) {
            mutableState.update { it.copy(dialog = null, note = note) }
            refresh()
        }

        /** A 404 on an item that was listed means it is gone, so the lists are read again. */
        private fun failed(failure: ApiResult.Failure) {
            mutableState.update { it.copy(dialog = null, note = failure.asNotice(item = true)) }
            if (failure.kind == FailureKind.NotFound) refresh()
        }

        private fun ApiResult.Failure.asNotice(item: Boolean) = PeopleNotice.Failed(kind, message, item)

        companion object {
            const val MAX_NAME = 80

            /**
             * Most recently heard first, never-heard last, then by name; by name alone while the server sends
             * no `lastSeenAt` (the plan's fallback for a server without the summary fields).
             */
            fun sorted(people: List<Person>): List<Person> {
                val byName = compareBy<Person> { it.name.lowercase() }
                if (people.none { it.lastSeenAt != null }) return people.sortedWith(byName)
                val seen = { person: Person -> person.lastSeenAt?.let(Formatting::parse) }
                return people.sortedWith(
                    compareBy<Person> { seen(it) == null }
                        .thenByDescending { seen(it) }
                        .then(byName),
                )
            }
        }
    }
