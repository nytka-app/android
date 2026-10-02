package io.github.nytka_app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Renews presence observation after a reboot, so the pendant coming into range starts capture. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var settings: SettingsStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                settings.current().pendantAddress?.let { address ->
                    // An association removed in system settings throws here; the app must still boot.
                    runCatching { CompanionPairing(context).observe(address) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
