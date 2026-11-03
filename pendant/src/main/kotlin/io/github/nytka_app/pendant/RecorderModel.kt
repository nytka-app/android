package io.github.nytka_app.pendant

import java.util.UUID

/**
 * The kinds of recorder Nytka can pair with. [id] is what settings store; pairing filters the companion
 * chooser on [serviceUuid] only, so the app never guesses the model from an advertisement.
 */
enum class RecorderModel(
    val id: String,
    val displayName: String,
    val serviceUuid: UUID,
    /** Where the protocol comes from. */
    val source: String,
    val capabilities: Capabilities,
) {
    /** The service is declared in `transport.c:135-136` and advertised at `:298`. */
    Omi(
        id = "omi",
        displayName = "Omi",
        serviceUuid = OmiUuids.AUDIO_SERVICE,
        source = "BasedHardware/omi at 2e34261, omi/firmware/omi/src/lib/core/transport.c",
        capabilities = OmiDriver.capabilities,
    ),

    /** Untested on real hardware: no Limitless pendant has been in reach. */
    Limitless(
        id = "limitless",
        displayName = "Limitless",
        serviceUuid = LimitlessUuids.SERVICE,
        source = "BasedHardware/omi at afad3b7, app/android/.../limitless/LimitlessProtocol.kt",
        capabilities = LimitlessDriver.capabilities,
    ),
    ;

    companion object {
        /** A missing or unknown id reads as [Omi]: installs from before models paired only an Omi. */
        fun of(id: String?): RecorderModel = entries.firstOrNull { it.id == id } ?: Omi
    }
}
