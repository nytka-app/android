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
    val muteSchedule: MuteSchedule = MuteSchedule(),
    /** Look for a newer pendant firmware on GitHub, at most once a day; on unless the user turns it off. */
    val firmwareCheck: Boolean = true,
    /** Epoch milliseconds of the last check GitHub answered; 0 when none. */
    val firmwareCheckedAt: Long = 0,
    /** The tag of the newest firmware release the last check found, such as `Omi_CV1_v3.0.21`. */
    val firmwareLatest: String? = null,
    /** Developer switch: ask Android for a high-priority connection after each setUp; off by default. */
    val highPriorityConnection: Boolean = false,
) {
    /** What the uploader watches: a change here may end a pause. */
    val connectionKey: Triple<String, String, Boolean> get() = Triple(serverUrl, token, privateNetwork)

    override fun toString() =
        "Settings(serverUrl=$serverUrl, token=${if (token.isEmpty()) "unset" else "set"}, " +
            "privateNetwork=$privateNetwork, muted=$muted, pendant=${pendantName ?: "none"}, " +
            "consentGiven=$consentGiven, onboarded=$onboarded, firstRunStep=$firstRunStep, " +
            "developerMode=$developerMode, fakePendant=$fakePendant, " +
            "alerts=$alertDisconnectedMinutes/$alertUnreachableMinutes/$alertBatteryPercent, " +
            "diagnosticsUpload=$diagnosticsUpload, muteWindows=${muteSchedule.windows.size}, " +
            "firmwareCheck=$firmwareCheck, highPriorityConnection=$highPriorityConnection)"
}
