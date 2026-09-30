package io.github.nytka_app.pendant

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OmiSettingsTest {
    private class Link(
        var features: Long? = (1L shl 7) or (1L shl 8),
    ) : SettingsLink {
        var led: Int? = 50
        var gain: Int? = 6
        var failWrites = false
        val ledWrites = mutableListOf<Int>()
        val gainWrites = mutableListOf<Int>()

        override suspend fun readFeatures() = features

        override suspend fun readLed() = led

        override suspend fun readGain() = gain

        override suspend fun writeLed(value: Int): Boolean = !failWrites && ledWrites.add(value).also { led = value }

        override suspend fun writeGain(value: Int): Boolean = !failWrites && gainWrites.add(value).also { gain = value }
    }

    @Test
    fun `the uuids are the firmware's settings characteristics`() {
        assertEquals(UUID.fromString("19b10011-e8f2-537e-4f6c-d104768a1214"), OmiUuids.LED_DIM)
        assertEquals(UUID.fromString("19b10012-e8f2-537e-4f6c-d104768a1214"), OmiUuids.MIC_GAIN)
        assertEquals(UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214"), OmiUuids.SETTINGS_SERVICE)
    }

    @Test
    fun `gain levels map to the firmware's decibels and level 0 is mute`() {
        assertEquals(listOf(null, -20, -10, 0, 6, 10, 20, 30, 40), (0..8).map(MicGain::decibels))
        assertTrue(MicGain.isMute(0))
        assertFalse(MicGain.isMute(1))
        assertTrue(LedDim.hidesStatus(0))
        assertFalse(LedDim.hidesStatus(1))
    }

    @Test
    fun `a setting byte reads as an unsigned value`() {
        assertEquals(100, OmiParsing.setting(byteArrayOf(100)))
        assertEquals(200, OmiParsing.setting(byteArrayOf(0xC8.toByte())))
        assertNull(OmiParsing.setting(byteArrayOf()))
        assertNull(OmiParsing.setting(null))
    }

    @Test
    fun `each feature bit gates its own setting`() =
        runTest {
            val cases =
                mapOf(
                    0L to SettingsSupport(),
                    (1L shl 6) to SettingsSupport(),
                    (1L shl 7) to SettingsSupport(led = true),
                    (1L shl 8) to SettingsSupport(gain = true),
                    (1L shl 6) or (1L shl 7) or (1L shl 8) to SettingsSupport(led = true, gain = true),
                )
            cases.forEach { (features, expected) ->
                val settings = OmiSettings(Link(features))
                settings.load()
                assertEquals("features $features", expected, settings.support.value)
            }
        }

    @Test
    fun `unread features mean nothing is offered and nothing is read`() =
        runTest {
            val settings = OmiSettings(Link(features = null))

            settings.load()

            assertEquals(SettingsSupport(), settings.support.value)
            assertEquals(SettingsValues(), settings.values.value)
        }

    @Test
    fun `load reads the values of the supported settings only`() =
        runTest {
            val settings = OmiSettings(Link((1L shl 8)))

            settings.load()

            assertEquals(SettingsValues(led = null, gain = 6), settings.values.value)
        }

    @Test
    fun `a failed read leaves that value unknown`() =
        runTest {
            val link = Link().apply { led = null }
            val settings = OmiSettings(link)

            settings.load()

            assertEquals(SettingsValues(led = null, gain = 6), settings.values.value)
        }

    @Test
    fun `writes go out clamped and update the value`() =
        runTest {
            val link = Link()
            val settings = OmiSettings(link)
            settings.load()

            assertTrue(settings.setLed(250))
            assertTrue(settings.setGain(-3))

            assertEquals(listOf(100), link.ledWrites)
            assertEquals(listOf(0), link.gainWrites)
            assertEquals(SettingsValues(led = 100, gain = 0), settings.values.value)
        }

    @Test
    fun `nothing is written for a setting the pendant does not report`() =
        runTest {
            val link = Link(features = (1L shl 8))
            val settings = OmiSettings(link)
            settings.load()

            assertFalse(settings.setLed(10))

            assertTrue(link.ledWrites.isEmpty())
        }

    @Test
    fun `nothing is written before load`() =
        runTest {
            val link = Link()
            val settings = OmiSettings(link)

            assertFalse(settings.setGain(3))
            assertTrue(link.gainWrites.isEmpty())
        }

    @Test
    fun `a failed write returns false, keeps the value and does not block the next one`() =
        runTest {
            val link = Link()
            val settings = OmiSettings(link)
            settings.load()

            link.failWrites = true
            assertFalse(settings.setLed(10))
            assertEquals(50, settings.values.value.led)

            link.failWrites = false
            assertTrue(settings.setLed(20))
            assertEquals(20, settings.values.value.led)
        }

    @Test
    fun `a lost link forgets support and values`() =
        runTest {
            val settings = OmiSettings(Link())
            settings.load()

            settings.linkLost()

            assertEquals(SettingsSupport(), settings.support.value)
            assertEquals(SettingsValues(), settings.values.value)
        }
}
