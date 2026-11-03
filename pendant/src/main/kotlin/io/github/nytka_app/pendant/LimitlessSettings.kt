package io.github.nytka_app.pendant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Limitless pendant's one setting, LED brightness (message 26). UNTESTED on real hardware. The pendant never
 * reports it, so [values] holds nothing until a write succeeds, and there is no gain: [setGain] is always false.
 */
internal class LimitlessSettings(
    private val link: LimitlessLink,
    private val commands: LimitlessCommands,
    private val log: (String) -> Unit = {},
) : PendantSettings {
    private val mutableSupport = MutableStateFlow(SettingsSupport())
    private val mutableValues = MutableStateFlow(SettingsValues())
    private val operations = Mutex()

    override val support: StateFlow<SettingsSupport> = mutableSupport
    override val values: StateFlow<SettingsValues> = mutableValues

    /** The LED can be set once connected; there is no feature bit to ask. */
    fun load() {
        mutableSupport.value = SettingsSupport(led = true)
    }

    fun linkLost() {
        mutableSupport.value = SettingsSupport()
        mutableValues.value = SettingsValues()
    }

    override suspend fun setLed(percent: Int): Boolean =
        operations.withLock {
            if (!mutableSupport.value.led) return@withLock false
            val value = percent.coerceIn(LedDim.MIN, LedDim.MAX)
            link.write(commands.setLed(value)).also { ok ->
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

    override suspend fun setGain(level: Int): Boolean = false
}
