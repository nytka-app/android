package io.github.nytka_app.core.settings

/** The steps of first run in order; [Settings.firstRunStep] is the one the user has reached. */
enum class FirstRunStep { Server, Permissions, Consent, Pairing }

data class Settings(
    val serverUrl: String = "",
    val token: String = "",
    val privateNetwork: Boolean = false,
    val muted: Boolean = false,
    val pendantAddress: String? = null,
    val pendantName: String? = null,
    val consentGiven: Boolean = false,
    val onboarded: Boolean = false,
    val firstRunStep: FirstRunStep = FirstRunStep.Server,
    val developerMode: Boolean = false,
    val fakePendant: Boolean = false,
    val alertDisconnectedMinutes: Int = 5,
    val alertUnreachableMinutes: Int = 15,
    val alertBatteryPercent: Int = 20,
    val diagnosticsUpload: Boolean = false,
) {
    /** What the uploader watches: a change here may end a pause. */
    val connectionKey: Triple<String, String, Boolean> get() = Triple(serverUrl, token, privateNetwork)

    override fun toString() =
        "Settings(serverUrl=$serverUrl, token=${if (token.isEmpty()) "unset" else "set"}, " +
            "privateNetwork=$privateNetwork, muted=$muted, pendant=${pendantName ?: "none"}, " +
            "consentGiven=$consentGiven, onboarded=$onboarded, firstRunStep=$firstRunStep, " +
            "developerMode=$developerMode, fakePendant=$fakePendant, " +
            "alerts=$alertDisconnectedMinutes/$alertUnreachableMinutes/$alertBatteryPercent, " +
            "diagnosticsUpload=$diagnosticsUpload)"
}
