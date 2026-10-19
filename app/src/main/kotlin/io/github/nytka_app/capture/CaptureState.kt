package io.github.nytka_app.capture

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.nytka_app.R

/**
 * The four capture states the owner sees, each with its own glyph, words and thread pattern, so none relies on
 * colour: the top bar, the status card, the notification and the widget all show these.
 */
enum class CaptureState(
    @StringRes val wordRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Recording(R.string.recording, R.drawable.ic_capture_recording),
    Muted(R.string.muted, R.drawable.ic_capture_muted),
    Waiting(R.string.capture_waiting, R.drawable.ic_capture_waiting),
    Off(R.string.capture_off, R.drawable.ic_capture_off),
    ;

    companion object {
        /** [muted] is the setting, which the UI shows at once; the controller's own flag follows it. */
        fun of(
            status: CaptureStatus,
            muted: Boolean = status.muted,
        ): CaptureState =
            when {
                !status.running -> Off
                muted -> Muted
                status.recording -> Recording
                else -> Waiting
            }
    }
}
