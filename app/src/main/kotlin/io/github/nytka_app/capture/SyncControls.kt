package io.github.nytka_app.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Device tab and developer mode see of the offline sync, and the four things they ask of it. */
interface SyncControls {
    val status: StateFlow<StorageSyncStatus>

    /** True while the pendant is connected: "Sync now" needs it. */
    val connected: StateFlow<Boolean>

    fun syncNow()

    fun stopSync()

    /** The answer to the first-sync backlog question: read it all (the default). */
    fun importBacklog()

    /** The answer to the first-sync backlog question: free the pendant's ring without reading it. */
    fun discardBacklog()
}

/** A sync that never starts: what the screens get where no sync runs (previews, tests). */
class IdleSyncControls : SyncControls {
    override val status: StateFlow<StorageSyncStatus> = MutableStateFlow(StorageSyncStatus()).asStateFlow()
    override val connected: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun syncNow() = Unit

    override fun stopSync() = Unit

    override fun importBacklog() = Unit

    override fun discardBacklog() = Unit
}
