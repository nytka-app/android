package io.github.nytka_app.ui.names

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The people a screen's text fields offer names from, read once per screen. Only people with a name count. A failure,
 * an older server without people included, offers none; the fields still work. Nothing is kept on the phone.
 */
@HiltViewModel
class KnownPeopleViewModel
    @Inject
    constructor(
        private val api: PeopleClient,
    ) : ViewModel() {
        private val mutablePeople = MutableStateFlow<List<Person>>(emptyList())
        val people: StateFlow<List<Person>> = mutablePeople.asStateFlow()

        init {
            viewModelScope.launch {
                mutablePeople.value = (api.people() as? ApiResult.Ok)?.value.orEmpty().filter { it.named }
            }
        }
    }
