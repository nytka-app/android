package io.github.nytka_app

import io.github.nytka_app.capture.PendantSettingsControls
import io.github.nytka_app.capture.PendantSettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePendantSettingsControls : PendantSettingsControls {
    val current = MutableStateFlow(PendantSettingsState())
    val calls = mutableListOf<String>()

    override val state: StateFlow<PendantSettingsState> = current

    override fun commitLed(percent: Int) {
        calls += "led $percent"
    }

    override fun commitGain(level: Int) {
        calls += "gain $level"
    }
}
