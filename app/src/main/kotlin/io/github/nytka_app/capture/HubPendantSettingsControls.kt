package io.github.nytka_app.capture

import kotlinx.coroutines.flow.StateFlow

/** [PendantSettingsControls] over the capture hub, which routes to the running service's controller. */
class HubPendantSettingsControls(
    private val hub: CaptureHub,
) : PendantSettingsControls {
    override val state: StateFlow<PendantSettingsState> = hub.settingsState

    override fun commitLed(percent: Int) = hub.commitLed(percent)

    override fun commitGain(level: Int) = hub.commitGain(level)
}
