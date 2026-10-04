package io.github.nytka_app.pendant

/**
 * The release stream of `BasedHardware/omi` that a pendant's firmware comes from. Omi names its tags by hardware
 * (`Omi_CV1_v3.0.21`, `Omi_DK2_v2.0.10`, `Friend_v1.0.4`), and the pendant reports no tag, only Device Information:
 * model `0x2A24` ("Omi CV 1", `omi/firmware/omi/omi.conf` at `8b55d6a`) and revision `0x2A26` (`3.0.21`). Only the
 * consumer pendant is mapped: Nytka supports no other, and a guess would point a user at the wrong firmware.
 */
enum class FirmwareStream(
    private val model: String,
    private val major: Int,
    val tagPrefix: String,
) {
    OmiCv1("Omi CV 1", 3, "Omi_CV1_v"),
    ;

    /** The version in [tag] (`Omi_CV1_v3.0.21`); null for another stream's tag and for suffixed ones (`..._pre`). */
    fun versionOf(tag: String): FirmwareVersion? =
        tag
            .takeIf { it.startsWith(tagPrefix) }
            ?.removePrefix(tagPrefix)
            ?.takeIf { VERSION.matches(it) }
            ?.let(FirmwareVersion::parse)

    companion object {
        private val VERSION = Regex("""\d{1,5}\.\d{1,5}\.\d{1,5}""")

        /** The stream of a pendant that reports [model] and [firmware]; null unless both match one exactly. */
        fun of(
            model: String?,
            firmware: String?,
        ): FirmwareStream? {
            val version = FirmwareVersion.parse(firmware) ?: return null
            val name = model?.trim()?.replace(Regex("\\s+"), " ") ?: return null
            return entries.firstOrNull { it.model.equals(name, ignoreCase = true) && it.major == version.major }
        }
    }
}
