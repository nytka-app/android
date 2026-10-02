package io.github.nytka_app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.itemNotice
import io.github.nytka_app.ui.notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The one dialog open on the People screen. [error] is what the server refused. */
sealed interface PeopleDialog {
    data class Actions(
        val person: Person,
    ) : PeopleDialog

    data class Rename(
        val person: Person,
        val error: String? = null,
    ) : PeopleDialog

    data class Merge(
        val person: Person,
    ) : PeopleDialog

    data class Delete(
        val person: Person,
    ) : PeopleDialog

    data class NameVoice(
        val voice: UnnamedVoice,
        val error: String? = null,
    ) : PeopleDialog
}

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val voices: List<UnnamedVoice> = emptyList(),
    val loading: Boolean = false,
    /** Why the lists could not be read; an older server says it needs an update. */
    val error: String? = null,
    val dialog: PeopleDialog? = null,
    /** The result of the last action, for a snackbar. */
    val note: String? = null,
)

@HiltViewModel
class PeopleViewModel
    @Inject
    constructor(
        private val api: PeopleClient,
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
                val failure = (people as? ApiResult.Failure) ?: (voices as? ApiResult.Failure)
                mutableState.update { current ->
                    if (failure != null) {
                        current.copy(loading = false, error = failure.notice())
                    } else {
                        current.copy(
                            people = (people as ApiResult.Ok).value.sortedBy { it.name.lowercase() },
                            voices = (voices as ApiResult.Ok).value,
                            loading = false,
                        )
                    }
                }
            }
        }

        fun openPerson(person: Person) = mutableState.update { it.copy(dialog = PeopleDialog.Actions(person)) }

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
                    is ApiResult.Ok -> done("Renamed to $trimmed.")
                    is ApiResult.Failure ->
                        if (result.kind == FailureKind.Conflict) {
                            mutableState.update {
                                it.copy(dialog = PeopleDialog.Rename(person, "Someone is already called $trimmed."))
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
                    is ApiResult.Ok -> done("Named the voice $trimmed.")
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
                    is ApiResult.Ok -> done("Merged ${person.name} into ${into.name}.")
                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        fun delete(person: Person) {
            viewModelScope.launch {
                when (val result = api.deletePerson(person.id)) {
                    is ApiResult.Ok -> done("Deleted ${person.name}.")
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
                                "Deleted ${person.name} and their voice model."
                            } else {
                                "Deleted ${person.name}, but the voice model could not be removed."
                            },
                        )

                    is ApiResult.Failure -> failed(result)
                }
            }
        }

        private fun done(note: String) {
            mutableState.update { it.copy(dialog = null, note = note) }
            refresh()
        }

        /** A 404 on an item that was listed means it is gone, so the lists are read again. */
        private fun failed(failure: ApiResult.Failure) {
            mutableState.update { it.copy(dialog = null, note = failure.itemNotice()) }
            if (failure.kind == FailureKind.NotFound) refresh()
        }

        companion object {
            const val MAX_NAME = 80
        }
    }
