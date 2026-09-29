package io.github.nytka_app

import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.core.diagnostics.DiagnosticSample

class FakeDeviceActions : DeviceActions {
    val calls = mutableListOf<String>()
    val shared = mutableListOf<List<DiagnosticSample>>()

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

    override suspend fun shareDiagnostics(samples: List<DiagnosticSample>) {
        calls += "share diagnostics"
        shared += samples
    }
}
