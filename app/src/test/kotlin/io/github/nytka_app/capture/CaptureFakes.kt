package io.github.nytka_app.capture

import io.github.nytka_app.core.queue.BookmarkSink
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.settings.MuteSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal class FakeSink : FrameSink {
    val frames = mutableListOf<Pair<UUID, Long>>()
    var seals = 0

    override suspend fun add(
        session: UUID,
        seq: Long,
        capturedAtMs: Long,
        payload: ByteArray,
    ) {
        frames += session to seq
    }

    override suspend fun seal(includePartialStored: Boolean): Int = 1.also { seals++ }
}

internal class FakeBookmarks : BookmarkSink {
    val kept = mutableListOf<Pair<Long, String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun add(
        id: String,
        atMs: Long,
        source: String,
    ) {
        gate?.await()
        kept += atMs to source
    }
}

internal class FakeSettings(
    muted: Boolean,
) : CaptureSettings {
    val state = MutableStateFlow(muted)
    override val muted = state
    val scheduleState = MutableStateFlow(MuteSchedule())
    override val schedule = scheduleState

    override suspend fun setMuted(muted: Boolean) {
        state.value = muted
    }
}
