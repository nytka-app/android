package io.github.nytka_app.core.location

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns one precise reading into a coarse place that is safe to send: the name of whichever [SavedPlace] it falls
 * inside (the nearest, when more than one does), or else a city-level label rounded to [GRID_DEGREES] (about 11 km),
 * so no reading ever leaves the phone as anything close to its exact spot.
 */
object PlaceCoarsener {
    const val GRID_DEGREES = 0.1

    fun coarsen(
        latitude: Double,
        longitude: Double,
        savedPlaces: List<SavedPlace> = emptyList(),
    ): String {
        val nearest =
            savedPlaces
                .map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
                .filter { (place, distanceMeters) -> distanceMeters <= place.radiusMeters }
                .minByOrNull { (_, distanceMeters) -> distanceMeters }
        return nearest?.first?.name ?: cityLevelLabel(latitude, longitude)
    }

    /** Great-circle distance between the two points, in meters. */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun cityLevelLabel(
        latitude: Double,
        longitude: Double,
    ): String = String.format(Locale.ROOT, "near %.1f,%.1f", gridded(latitude), gridded(longitude))

    // The +0.0 turns a -0.0 result (e.g. rounding -0.02) into 0.0, so the label never shows a bare minus sign.
    private fun gridded(value: Double): Double = round(value / GRID_DEGREES) * GRID_DEGREES + 0.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
