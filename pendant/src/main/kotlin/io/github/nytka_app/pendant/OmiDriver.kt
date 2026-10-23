package io.github.nytka_app.pendant

/** What is specific to the Omi pendant (`BasedHardware/omi` at 2e34261, `omi/firmware/omi/src`). */
object OmiDriver {
    val capabilities =
        Capabilities(
            storage = StorageKind.Custody,
            buttons = setOf(ButtonEvent.SingleTap, ButtonEvent.DoubleTap, ButtonEvent.Release),
            haptic = true,
            audioSwitch = true,
            settings = SettingsSupport(led = true, gain = true),
            firmware = FirmwareStream.OmiCv1,
            codec = OPUS_FS320.toByte(),
        )

    /** The events the consumer firmware sends (`lib/core/button.c`); 3 and 4 are defined but never sent. */
    fun button(code: Int): ButtonEvent? =
        when (code) {
            1 -> ButtonEvent.SingleTap
            2 -> ButtonEvent.DoubleTap
            5 -> ButtonEvent.Release
            else -> null
        }

    /** The values the haptic characteristic takes (`haptic.c`). */
    fun hapticCode(haptic: Haptic): Byte =
        when (haptic) {
            Haptic.Short -> 1
            Haptic.Medium -> 2
            Haptic.Long -> 3
        }
}
