package io.github.nytka_app.core.settings

import io.github.nytka_app.core.location.SavedPlace

/** The steps of first run in order; [Settings.firstRunStep] is the one the user has reached. */
enum class FirstRunStep { Server, Permissions, Consent, Pairing }

data class Settings(
    val serverUrl: String = "",
    val token: String = "",
    val privateNetwork: Boolean = false,
    val muted: Boolean = false,
    val pendantAddress: String? = null,
    val pendantName: String? = null,
    /** The paired recorder's `RecorderModel.id`; null, from installs before models, reads as Omi. */
    val pendantModel: String? = null,
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
    /** Post a notification for each meeting brief the server makes; off until switched on. */
    val briefNotifications: Boolean = false,
    /** Ids of the briefs already posted, so one posts once; kept to those still listed, at most 50. */
    val notifiedBriefs: List<String> = emptyList(),
    /** Play a short chime every [consentChimeMinutes] while the pendant records; off until switched on. */
    val consentChime: Boolean = false,
    val consentChimeMinutes: Int = 15,
    /** Tell the server when the phone plays sound through its speaker or is in a call: times only; on by default. */
    val phoneContext: Boolean = true,
    /** Follow the wallpaper's colours instead of linen and ink; off until switched on. */
    val wallpaperColors: Boolean = false,
    /** Tag each conversation with a coarse place (a saved place or a city-level label); off until switched on. */
    val locationTags: Boolean = false,
    /** Places the user has named, such as "Home" or "Office"; checked nearest-first by [locationTags]. */
    val savedPlaces: List<SavedPlace> = emptyList(),
    /** The place the last session recorded, waiting to be matched to the conversation it becomes; null when none is. */
    val pendingLocationPlace: String? = null,
    /** Epoch milliseconds the session that made [pendingLocationPlace] started; 0 when there is none. */
    val pendingLocationAt: Long = 0,
    /** Recognise speech on the phone too, as local draft text; off until switched on. Needs an imported model. */
    val phoneTranscription: Boolean = false,
    /** Also recognise on battery at [phoneTranscriptionBatteryPercent] or more; otherwise only on a charger. */
    val phoneTranscriptionOnBattery: Boolean = false,
    val phoneTranscriptionBatteryPercent: Int = 80,
) {
    /** What the uploader watches: a change here may end a pause. */
    val connectionKey: Triple<String, String, Boolean> get() = Triple(serverUrl, token, privateNetwork)

    override fun toString() =
        "Settings(serverUrl=$serverUrl, token=${if (token.isEmpty()) "unset" else "set"}, " +
            "privateNetwork=$privateNetwork, muted=$muted, pendant=${pendantName ?: "none"}, " +
            "pendantModel=${pendantModel ?: "unset"}, " +
            "consentGiven=$consentGiven, onboarded=$onboarded, firstRunStep=$firstRunStep, " +
            "developerMode=$developerMode, fakePendant=$fakePendant, " +
            "alerts=$alertDisconnectedMinutes/$alertUnreachableMinutes/$alertBatteryPercent, " +
            "diagnosticsUpload=$diagnosticsUpload, muteWindows=${muteSchedule.windows.size}, " +
            "firmwareCheck=$firmwareCheck, highPriorityConnection=$highPriorityConnection, " +
            "phoneContext=$phoneContext, wallpaperColors=$wallpaperColors, " +
            "locationTags=$locationTags, savedPlaces=${savedPlaces.size}, " +
            "phoneTranscription=$phoneTranscription/$phoneTranscriptionOnBattery/$phoneTranscriptionBatteryPercent)"
}
