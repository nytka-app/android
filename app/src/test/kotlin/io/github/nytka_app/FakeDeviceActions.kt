package io.github.nytka_app

import io.github.nytka_app.capture.DeviceActions

class FakeDeviceActions : DeviceActions {
    val calls = mutableListOf<String>()

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
}
