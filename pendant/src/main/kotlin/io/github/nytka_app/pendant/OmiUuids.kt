package io.github.nytka_app.pendant

import java.util.UUID

/**
 * Omi consumer firmware 3.0.x (`BasedHardware/omi` at `2e34261`, `omi/firmware/omi`). The features, time and
 * storage UUIDs come from `src/lib/core/transport.c` and `src/lib/core/storage.c`, identical at `a2d37dea`.
 */
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
    val FEATURES: UUID = UUID.fromString("19b10021-e8f2-537e-4f6c-d104768a1214")
    val TIME_SYNC_SERVICE: UUID = UUID.fromString("19b10030-e8f2-537e-4f6c-d104768a1214")
    val TIME_SYNC_WRITE: UUID = UUID.fromString("19b10031-e8f2-537e-4f6c-d104768a1214")
    val TIME_SYNC_READ: UUID = UUID.fromString("19b10032-e8f2-537e-4f6c-d104768a1214")
    val STORAGE_SERVICE: UUID = UUID.fromString("30295780-4301-eabd-2904-2849adfeae43")
    val STORAGE_CONTROL: UUID = UUID.fromString("30295781-4301-eabd-2904-2849adfeae43")
    val STORAGE_STATUS: UUID = UUID.fromString("30295782-4301-eabd-2904-2849adfeae43")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

const val OPUS_FS320 = 21
