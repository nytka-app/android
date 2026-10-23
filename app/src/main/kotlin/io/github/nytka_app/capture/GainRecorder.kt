package io.github.nytka_app.capture

import android.os.SystemClock
import io.github.nytka_app.core.context.ContextRange
import io.github.nytka_app.core.context.GainRanges
import io.github.nytka_app.core.queue.ContextRow
import io.github.nytka_app.core.queue.ContextSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Records the microphone gain level the pendant reports, for as long as [record] runs, as gain ranges in the context
 * outbox. Only what the pendant says is recorded; nothing here writes the gain.
 */
class GainRecorder(
    private val sink: ContextSink,
    private val writes: CoroutineScope,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val ranges = GainRanges(SystemClock::elapsedRealtime, ::store)

    /** Follows [levels] (null while the pendant is away or reports none) until cancelled, then ends the open range. */
    suspend fun record(levels: Flow<Int?>) {
        try {
            levels.distinctUntilChanged().collect(ranges::onGain)
        } finally {
            ranges.onStop()
        }
    }

    private fun store(range: ContextRange) {
        val offset = wallClock() - SystemClock.elapsedRealtime()
        val row =
            ContextRow(
                UUID.randomUUID().toString(),
                range.kind.wire,
                range.route.wire,
                range.startMs + offset,
                range.endMs + offset,
                range.detail,
            )
        // The capture scope may be ending: the write runs on the app's scope.
        writes.launch { sink.add(row) }
    }
}
