package io.github.nytka_app

import io.github.nytka_app.capture.DeviceActions

class FakeDeviceActions : DeviceActions {
    val calls = mutableListOf<String>()
    var sharedCount = 0
    var shareFailure: Exception? = null

    /** What Android answers about the local network; allowed unless a test says otherwise. */
    var localNetwork = true

    override fun forgetPendant(address: String) {
        calls += "forget $address"
    }

    override fun startCapture() {
        calls += "start"
    }

    override fun restartCapture() {
        calls += "restart"
    }

    override fun stopCapture() {
        calls += "stop"
    }

    override fun localNetworkGranted() = localNetwork

    override fun openAppSettings() {
        calls += "open settings"
    }

    override suspend fun shareDiagnostics(): Int {
        calls += "share diagnostics"
        shareFailure?.let { throw it }
        return sharedCount
    }
}
