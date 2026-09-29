package io.github.nytka_app.capture

import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

/** [SyncControls] over the capture hub, which routes to the running service's sync. */
class HubSyncControls(
    private val hub: CaptureHub,
    scope: CoroutineScope,
) : SyncControls {
    override val status: StateFlow<StorageSyncStatus> = hub.syncStatus
    override val connected: StateFlow<Boolean> =
        hub.status
            .map { it.connection is PendantConnection.Connected }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun syncNow() = hub.syncNow()

    override fun stopSync() = hub.stopSync()

    override fun importBacklog() = hub.importBacklog()

    override fun discardBacklog() = hub.discardBacklog()
}
