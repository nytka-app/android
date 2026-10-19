package io.github.nytka_app.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A reading coarse enough to send through [io.github.nytka_app.core.location.PlaceCoarsener]: never more precise. */
fun interface CoarseLocationSource {
    /** Android's last known fix, or null without the permission or without one yet. */
    suspend fun lastKnown(): Pair<Double, Double>?
}

/** Reads whatever provider last reported a fix; it asks none to turn on, so it never wakes the GPS. */
class AndroidCoarseLocationSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CoarseLocationSource {
        override suspend fun lastKnown(): Pair<Double, Double>? {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            return manager.allProviders
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        }
    }
