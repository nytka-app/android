package io.github.nytka_app.core.queue

import java.util.UUID

/** Where captured frames go. [FrameQueue] in the app. */
interface FrameSink {
    suspend fun add(
        session: UUID,
        seq: Long,
        capturedAtMs: Long,
        payload: ByteArray,
    )

    suspend fun seal(): Int
}
