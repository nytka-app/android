package io.github.nytka_app.core.diagnostics

import kotlinx.serialization.Serializable

/**
 * One line of the app's own log, stored and uploaded next to the samples: the server keeps any JSON object
 * with an `id` and an `at`, and tells the two apart by `kind`. Nothing else goes in: no audio, no transcript,
 * no server URL, no token.
 */
@Serializable
data class DiagnosticLogEvent(
    val id: String,
    val at: String,
    val kind: String,
    /** `D`, `I`, `W` or `E`. */
    val level: String,
    val tag: String,
    val message: String,
    val appVersion: String,
    val device: String,
) {
    companion object {
        const val KIND = "log"
    }
}
