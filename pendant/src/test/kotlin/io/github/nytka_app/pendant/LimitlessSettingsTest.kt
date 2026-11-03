package io.github.nytka_app.pendant

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitlessSettingsTest {
    @Test
    fun `nothing is written before the pendant is connected`() =
        runTest {
            val pendant = FakeLimitless(backgroundScope)
            val settings = LimitlessSettings(pendant, LimitlessCommands())
            assertFalse(settings.support.value.led)
            assertFalse(settings.setLed(50))
            assertTrue(pendant.ledWrites.isEmpty())
        }

    @Test
    fun `the LED is written and remembered, within 0 to 100`() =
        runTest {
            val pendant = FakeLimitless(backgroundScope)
            val settings = LimitlessSettings(pendant, LimitlessCommands()).also { it.load() }
            assertTrue(settings.support.value.led)
            assertFalse(settings.support.value.gain)
            assertNull(settings.values.value.led)
            assertTrue(settings.setLed(40))
            assertEquals(40, settings.values.value.led)
            assertTrue(settings.setLed(250))
            assertEquals(listOf(40L, 100L), pendant.ledWrites)
        }

    @Test
    fun `a failed write keeps the old value`() =
        runTest {
            val pendant = FakeLimitless(backgroundScope)
            val settings = LimitlessSettings(pendant, LimitlessCommands()).also { it.load() }
            settings.setLed(40)
            pendant.failWrites = true
            assertFalse(settings.setLed(10))
            assertEquals(40, settings.values.value.led)
        }

    @Test
    fun `the pendant has no gain to set`() =
        runTest {
            val pendant = FakeLimitless(backgroundScope)
            val settings = LimitlessSettings(pendant, LimitlessCommands()).also { it.load() }
            assertFalse(settings.setGain(3))
            assertTrue(pendant.commands.isEmpty())
        }

    @Test
    fun `a lost link clears what was supported and remembered`() =
        runTest {
            val pendant = FakeLimitless(backgroundScope)
            val settings = LimitlessSettings(pendant, LimitlessCommands()).also { it.load() }
            settings.setLed(40)
            settings.linkLost()
            assertFalse(settings.support.value.led)
            assertNull(settings.values.value.led)
        }
}
