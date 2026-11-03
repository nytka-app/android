package io.github.nytka_app.pendant

/**
 * What is specific to the Limitless pendant (`BasedHardware/omi` at afad3b7, see [LimitlessProtocol]).
 * UNTESTED on real hardware: no Limitless pendant has been in reach, so every fact here is the upstream source's.
 */
object LimitlessDriver {
    /**
     * Storage: flash pages, read and freed by index. Buttons: a double press only. No haptic motor is known. The audio
     * switch is the mode (message 8): `{0, 0}` stops the stream while the pendant records to flash, as the Omi
     * switch does. LED brightness only. No firmware stream. Opus at 20 ms; a pendant that sends 10 ms packets has
     * them joined ([OpusFrames]).
     */
    val capabilities =
        Capabilities(
            storage = StorageKind.Custody,
            buttons = setOf(ButtonEvent.DoubleTap),
            haptic = false,
            audioSwitch = true,
            settings = SettingsSupport(led = true, gain = false),
            firmware = null,
            codec = OPUS_FS320.toByte(),
        )
}
