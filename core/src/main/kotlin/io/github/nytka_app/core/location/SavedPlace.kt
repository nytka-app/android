package io.github.nytka_app.core.location

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A place the user has named, such as "Home" or "Office". [radiusMeters] is how close a reading must be to count as
 * this place; it never leaves the phone, only [name] does.
 */
@Serializable
data class SavedPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 150.0
        private val listSerializer = ListSerializer(serializer())

        /** For DataStore: JSON, so a place's name can hold anything a tag can. */
        fun encodeList(places: List<SavedPlace>): String = Json.encodeToString(listSerializer, places)

        /** What [encodeList] wrote; damaged text loses every place rather than only some, so it is never guessed at. */
        fun decodeList(text: String?): List<SavedPlace> =
            text?.let { runCatching { Json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
    }
}
