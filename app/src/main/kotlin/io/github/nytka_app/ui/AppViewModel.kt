package io.github.nytka_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.core.settings.SettingsSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chooses first run or the tabs, and makes sure capture runs once the app is set up. */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settings: SettingsSource,
        actions: DeviceActions,
    ) : ViewModel() {
        val onboarded: StateFlow<Boolean?> =
            settings.settings.map { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        init {
            viewModelScope.launch {
                val current = settings.current()
                if (current.onboarded && (current.pendantAddress != null || current.fakePendant)) actions.startCapture()
            }
        }
    }
