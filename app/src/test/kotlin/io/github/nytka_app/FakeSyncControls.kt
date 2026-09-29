package io.github.nytka_app

import io.github.nytka_app.capture.StorageSyncStatus
import io.github.nytka_app.capture.SyncControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSyncControls : SyncControls {
    val sync = MutableStateFlow(StorageSyncStatus())
    val link = MutableStateFlow(true)
    val calls = mutableListOf<String>()

    override val status: StateFlow<StorageSyncStatus> = sync
    override val connected: StateFlow<Boolean> = link

    override fun syncNow() {
        calls += "sync now"
    }

    override fun stopSync() {
        calls += "stop"
    }

    override fun importBacklog() {
        calls += "import"
    }

    override fun discardBacklog() {
        calls += "discard"
    }
}
