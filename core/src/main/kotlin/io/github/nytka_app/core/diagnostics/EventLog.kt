package io.github.nytka_app.core.diagnostics

import android.util.Log

/**
 * Where call sites write their log lines. Every line reaches logcat as before; [AppLog] also keeps it in the
 * diagnostics table. Pass only counters, status words and redacted addresses: never audio, transcript text,
 * the server URL or the token.
 */
interface EventLog {
    /** [priority] is one of android.util.Log's constants. */
    fun log(
        priority: Int,
        tag: String,
        message: String,
    )

    fun d(
        tag: String,
        message: String,
    ) = log(Log.DEBUG, tag, message)

    fun i(
        tag: String,
        message: String,
    ) = log(Log.INFO, tag, message)

    fun w(
        tag: String,
        message: String,
    ) = log(Log.WARN, tag, message)

    fun e(
        tag: String,
        message: String,
    ) = log(Log.ERROR, tag, message)

    /** Logcat only: what a class uses when nobody hands it an [AppLog]. */
    object Logcat : EventLog {
        override fun log(
            priority: Int,
            tag: String,
            message: String,
        ) {
            Log.println(priority, tag, message)
        }
    }
}
