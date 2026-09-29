package io.github.nytka_app.capture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the screens ask of the system; a fake in tests. */
interface DeviceActions {
    fun forgetPendant(address: String)

    fun startCapture()

    fun restartCapture()

    fun stopCapture()
}

class AndroidDeviceActions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceActions {
        override fun forgetPendant(address: String) = CompanionPairing(context).forget(address)

        override fun startCapture() = CaptureService.start(context)

        override fun restartCapture() = CaptureService.restart(context)

        override fun stopCapture() = CaptureService.stop(context)
    }
