package io.github.nytka_app.pendant

import java.util.UUID

/** Omi consumer firmware 3.0.x (`BasedHardware/omi` at `2e34261`, `omi/firmware/omi`). */
object OmiUuids {
    val AUDIO_SERVICE: UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    val AUDIO_DATA: UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
    val AUDIO_CODEC: UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")
    val BUTTON: UUID = UUID.fromString("23ba7925-0000-1000-7450-346eac492e92")
    val HAPTIC: UUID = UUID.fromString("cab1ab96-2ea5-4f4d-bb56-874b72cfc984")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val HARDWARE_REVISION: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

const val OPUS_FS320 = 21
