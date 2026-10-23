package io.github.nytka_app.pendant

/** Whether a recorder keeps audio while the phone is away. */
enum class StorageKind {
    None,

    /** Audio is read, then freed up to a position the server has accepted. */
    Custody,
}

/**
 * What a recorder can do, fixed per model. The app reads this and never asks which model it is: a missing
 * capability hides its control or turns its action into a no-op.
 */
data class Capabilities(
    val storage: StorageKind,
    /** The button events the recorder can send; empty for none. */
    val buttons: Set<ButtonEvent>,
    val haptic: Boolean,
    /** The recorder can stop sending audio on request; without it the driver drops frames while audio is off. */
    val audioSwitch: Boolean,
    /** The settings the recorder can have; each still has to be reported by the connected recorder. */
    val settings: SettingsSupport,
    /** The release stream of its firmware; null means no update notice. */
    val firmware: FirmwareStream?,
    /** The chunk codec its frames are in (`ChunkFormat`). */
    val codec: Byte,
)
