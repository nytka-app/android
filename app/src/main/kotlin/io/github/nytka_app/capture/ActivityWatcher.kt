package io.github.nytka_app.capture

import io.github.nytka_app.core.context.Activity

/** Android's activity transitions: hears when walking, driving or sitting still begins and ends. */
interface ActivityWatcher {
    /**
     * Starts listening, if this build can and the permission is granted; [onActivity] hears the activity entered, or
     * null when the one entered last ends, with the time on the elapsed clock. Returns whether it listens.
     */
    fun start(onActivity: (Activity?, Long) -> Unit): Boolean

    fun stop()
}
