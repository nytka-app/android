package io.github.nytka_app.capture

import android.content.Context
import io.github.nytka_app.core.context.Activity

/** The oss build has no Google Play services, so no activity transitions: the switch is not shown. */
@Suppress("UnusedPrivateProperty")
class TransitionWatcher(
    private val context: Context,
) : ActivityWatcher {
    override fun start(onActivity: (Activity?, Long) -> Unit) = false

    override fun stop() = Unit

    companion object {
        const val AVAILABLE = false
    }
}
