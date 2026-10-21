package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.settings.Settings

/** Whether the phone may spend battery on recognition now: on a charger, or above the threshold the user chose. */
object PowerGate {
    fun allows(
        settings: Settings,
        level: Int?,
        charging: Boolean?,
    ): Boolean {
        if (!settings.phoneTranscription) return false
        if (charging == true) return true
        return settings.phoneTranscriptionOnBattery &&
            level != null &&
            level >= settings.phoneTranscriptionBatteryPercent
    }
}
