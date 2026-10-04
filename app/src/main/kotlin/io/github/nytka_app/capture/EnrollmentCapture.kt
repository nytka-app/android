package io.github.nytka_app.capture

import io.github.nytka_app.pendant.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What voice enrollment needs of live capture: whether the pendant sends audio, and its frames for a while. */
interface EnrollmentCapture {
    /** True while capture runs, the pendant is connected and nothing mutes it. */
    val recording: StateFlow<Boolean>

    /**
     * Sends live frames to [sink] instead of the upload queue until [resume]. False, changing nothing, while capture
     * does not run.
     */
    fun divert(sink: (AudioFrame) -> Unit): Boolean

    /** Live frames go to the upload queue again; harmless when nothing is diverted. */
    fun resume()
}

/** [EnrollmentCapture] over the capture hub, which routes to the running service's controller. */
class HubEnrollmentCapture(
    private val hub: CaptureHub,
    scope: CoroutineScope,
) : EnrollmentCapture {
    override val recording: StateFlow<Boolean> =
        hub.status.map { it.recording }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun divert(sink: (AudioFrame) -> Unit) = hub.divert(sink)

    override fun resume() = hub.undivert()
}
