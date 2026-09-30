package io.github.nytka_app.pendant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which of the pendant's two settings it reports (feature bits 7 and 8 of `19b10021`); both false until known. */
data class SettingsSupport(
    val led: Boolean = false,
    val gain: Boolean = false,
) {
    val any: Boolean get() = led || gain
}

/** What the pendant holds now; null until read, and after the link is lost. */
data class SettingsValues(
    val led: Int? = null,
    val gain: Int? = null,
)

/** LED brightness (`19b10011`): 0 to 100 percent for every status LED; the firmware caps a larger byte at 100. */
object LedDim {
    const val MIN = 0
    const val MAX = 100

    /** At 0 the pendant's link and charging lights are off too. */
    fun hidesStatus(percent: Int) = percent == MIN
}

/**
 * Microphone gain (`19b10012`): a level from 0 to 8 that the firmware maps to the PDM gain register in
 * `omi/firmware/omi/src/mic.c`, `mic_set_gain`: 0 mute, then -20, -10, 0, +6, +10, +20 (the default, level 6),
 * +30 and +40 dB.
 * The firmware caps a larger byte at 8.
 */
object MicGain {
    const val MIN = 0
    const val MAX = 8
    private val DECIBELS = intArrayOf(-20, -10, 0, 6, 10, 20, 30, 40)

    fun isMute(level: Int) = level == MIN

    /** The gain in dB, or null for level 0, which is mute. */
    fun decibels(level: Int): Int? = if (level in 1..MAX) DECIBELS[level - 1] else null
}

/** The pendant's settings service, kept in its flash by the firmware; the firmware applies a write at once. */
interface PendantSettings {
    val support: StateFlow<SettingsSupport>
    val values: StateFlow<SettingsValues>

    /** Writes [percent] (clamped to 0..100); false when unsupported or the write failed. Never throws. */
    suspend fun setLed(percent: Int): Boolean

    /** Writes [level] (clamped to 0..8); false when unsupported or the write failed. Never throws. */
    suspend fun setGain(level: Int): Boolean
}

/** What [OmiSettings] needs from the Bluetooth link; [OmiPendant] and [FakeRing] implement it. */
interface SettingsLink {
    /** The features characteristic (`19b10021`), a u32 little-endian; bit 7 is LED dimming, bit 8 mic gain. */
    suspend fun readFeatures(): Long?

    /** The one byte of `19b10011`, or null. */
    suspend fun readLed(): Int?

    /** The one byte of `19b10012`, or null. */
    suspend fun readGain(): Int?

    suspend fun writeLed(value: Int): Boolean

    suspend fun writeGain(value: Int): Boolean
}

/**
 * The settings service over a [SettingsLink]. Nothing is written until the features say the pendant has the setting;
 * [load] runs after every connection and [linkLost] when it ends.
 */
class OmiSettings(
    private val link: SettingsLink,
    private val log: (String) -> Unit = {},
) : PendantSettings {
    private val mutableSupport = MutableStateFlow(SettingsSupport())
    private val mutableValues = MutableStateFlow(SettingsValues())
    private val operations = Mutex()

    override val support: StateFlow<SettingsSupport> = mutableSupport
    override val values: StateFlow<SettingsValues> = mutableValues

    /** Reads the features, then the value of each setting the pendant has. A failed read leaves that value null. */
    suspend fun load() {
        operations.withLock {
            val features = link.readFeatures()
            val found = SettingsSupport(led = hasFeature(features, LED_BIT), gain = hasFeature(features, GAIN_BIT))
            mutableSupport.value = found
            mutableValues.value =
                SettingsValues(
                    led = if (found.led) link.readLed()?.coerceIn(LedDim.MIN, LedDim.MAX) else null,
                    gain = if (found.gain) link.readGain()?.coerceIn(MicGain.MIN, MicGain.MAX) else null,
                )
            log("settings: $found, ${mutableValues.value}")
        }
    }

    fun linkLost() {
        mutableSupport.value = SettingsSupport()
        mutableValues.value = SettingsValues()
    }

    override suspend fun setLed(percent: Int): Boolean =
        operations.withLock {
            if (!mutableSupport.value.led) return@withLock false
            val value = percent.coerceIn(LedDim.MIN, LedDim.MAX)
            link.writeLed(value).also { ok ->
                if (ok) {
                    mutableValues.value =
                        mutableValues.value.copy(
                            led = value,
                        )
                } else {
                    log("settings: led write failed")
                }
            }
        }

    override suspend fun setGain(level: Int): Boolean =
        operations.withLock {
            if (!mutableSupport.value.gain) return@withLock false
            val value = level.coerceIn(MicGain.MIN, MicGain.MAX)
            link.writeGain(value).also { ok ->
                if (ok) {
                    mutableValues.value =
                        mutableValues.value.copy(
                            gain = value,
                        )
                } else {
                    log("settings: gain write failed")
                }
            }
        }

    companion object {
        const val LED_BIT = 7
        const val GAIN_BIT = 8

        fun hasFeature(
            features: Long?,
            bit: Int,
        ): Boolean = features != null && (features shr bit) and 1L == 1L
    }
}
