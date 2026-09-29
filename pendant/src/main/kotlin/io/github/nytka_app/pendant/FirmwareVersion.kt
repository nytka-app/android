package io.github.nytka_app.pendant

/** A firmware revision such as `3.0.21` (Device Information `0x2A26`); [parse] ignores anything after the patch. */
data class FirmwareVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion): Int =
        compareValuesBy(this, other, FirmwareVersion::major, FirmwareVersion::minor, FirmwareVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** The first revision with the ring protocol (`Omi_CV1_v3.0.20`); older ones have a file protocol. */
        val RING_STORAGE = FirmwareVersion(3, 0, 20)

        private val PATTERN = Regex("""^v?(\d{1,5})\.(\d{1,5})\.(\d{1,5})""")

        fun parse(text: String?): FirmwareVersion? {
            val (major, minor, patch) = PATTERN.find(text?.trim().orEmpty())?.destructured ?: return null
            return FirmwareVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
