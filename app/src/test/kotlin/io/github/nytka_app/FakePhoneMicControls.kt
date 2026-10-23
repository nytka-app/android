package io.github.nytka_app

import io.github.nytka_app.phonemic.PhoneMicControls
import io.github.nytka_app.phonemic.PhoneMicStatus
import kotlinx.coroutines.flow.MutableStateFlow

class FakePhoneMicControls : PhoneMicControls {
    override val status = MutableStateFlow<PhoneMicStatus>(PhoneMicStatus.Off)
    var encoder = true
    val calls = mutableListOf<String>()

    override fun available() = encoder

    override fun start() {
        calls += "start"
        status.value = PhoneMicStatus.Recording
    }

    override fun stop() {
        calls += "stop"
        status.value = PhoneMicStatus.Off
    }
}
