package io.github.nytka_app.capture

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build

/** The system binds this when the paired pendant comes into range, even after a reboot. */
class NytkaCompanionService : CompanionDeviceService() {
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = CaptureService.start(this)

    @Deprecated("Called before Android 13 only")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) CaptureService.start(this)
    }
}
