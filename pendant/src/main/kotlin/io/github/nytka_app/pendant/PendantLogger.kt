package io.github.nytka_app.pendant

import android.util.Log

/**
 * Where [OmiPendant] writes its log lines; the app hands it one that also keeps them in its diagnostics.
 * [priority] is one of android.util.Log's constants. Lines carry no audio and only redacted addresses.
 */
fun interface PendantLogger {
    fun log(
        priority: Int,
        tag: String,
        message: String,
    )

    companion object {
        /** Logcat only. */
        val Android = PendantLogger { priority, tag, message -> Log.println(priority, tag, message) }
    }
}
