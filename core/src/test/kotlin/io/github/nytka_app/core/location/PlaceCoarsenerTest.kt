package io.github.nytka_app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCoarsenerTest {
    @Test
    fun `with no saved places it answers a city-level grid label`() {
        val place = PlaceCoarsener.coarsen(40.7128, -74.0060)

        assertEquals("near_n40_7_w74_0", place)
    }

    @Test
    fun `the grid label uses only letters, digits and underscore, as the server's tag format requires`() {
        val place = PlaceCoarsener.coarsen(40.7128, -74.0060)

        assertTrue(place.matches(Regex("[a-z][a-z0-9_]{0,31}")))
    }

    @Test
    fun `inside a saved place's radius it answers the place's name`() {
        val home = SavedPlace("Home", latitude = 40.7128, longitude = -74.0060, radiusMeters = 150.0)

        // About 50 m north of Home: inside the radius.
        val place = PlaceCoarsener.coarsen(40.7133, -74.0060, listOf(home))

        assertEquals("Home", place)
    }

    @Test
    fun `outside every saved place's radius it falls back to the grid label`() {
        val home = SavedPlace("Home", latitude = 40.7128, longitude = -74.0060, radiusMeters = 150.0)

        // About 1.1 km away: outside the radius.
        val place = PlaceCoarsener.coarsen(40.7228, -74.0060, listOf(home))

        assertEquals("near_n40_7_w74_0", place)
    }

    @Test
    fun `a reading inside two saved places picks the nearer one`() {
        val home = SavedPlace("Home", latitude = 40.7128, longitude = -74.0060, radiusMeters = 500.0)
        val office = SavedPlace("Office", latitude = 40.7130, longitude = -74.0060, radiusMeters = 500.0)

        val place = PlaceCoarsener.coarsen(40.71305, -74.0060, listOf(home, office))

        assertEquals("Office", place)
    }

    @Test
    fun `a distance exactly at the radius still counts as inside`() {
        val oneDegreeLatMeters = PlaceCoarsener.distanceMeters(0.0, 0.0, 1.0, 0.0)
        val place = SavedPlace("Edge", latitude = 0.0, longitude = 0.0, radiusMeters = oneDegreeLatMeters)

        assertEquals("Edge", PlaceCoarsener.coarsen(1.0, 0.0, listOf(place)))
    }

    @Test
    fun `the grid label never shows a bare minus sign for a reading near zero`() {
        val place = PlaceCoarsener.coarsen(-0.02, 0.02)

        assertEquals("near_n0_0_e0_0", place)
    }

    @Test
    fun `the grid label rounds to the nearer multiple of the grid size, not just truncating`() {
        val place = PlaceCoarsener.coarsen(40.74, -74.06)

        assertEquals("near_n40_7_w74_1", place)
    }
}
