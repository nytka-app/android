package io.github.nytka_app.capture

import android.content.Context
import android.os.BatteryManager

/** The phone's own battery right now; a fake in tests. */
interface PhoneBattery {
    /** Percent, 0 to 100, or null if the phone cannot report it. */
    val level: Int?
    val charging: Boolean?
}

/** [PhoneBattery] as Android's [BatteryManager] reports it. */
class AndroidPhoneBattery(
    private val context: Context,
) : PhoneBattery {
    override val level: Int?
        get() =
            manager()
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }

    override val charging: Boolean?
        get() = manager()?.isCharging

    private fun manager() = context.getSystemService(BatteryManager::class.java)
}
