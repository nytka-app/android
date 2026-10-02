package io.github.nytka_app.core.diagnostics

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Where [AppLog] puts log events. [DiagnosticsLog] in the app. */
interface LogEventSink {
    suspend fun addLog(event: DiagnosticLogEvent)
}

/**
 * Writes every line to logcat, and between [start] and [stop] also to the diagnostics table on [io], so the
 * lines upload with the samples. Logging never blocks or throws: a line is queued, and when the queue of
 * [capacity] is full the oldest waiting line is dropped and counted; the writer then adds one line saying
 * how many. More than [hourlyLimit] lines in one clock hour are dropped, after one line saying so.
 *
 * Each [start] opens a fresh queue and returns its generation; [stop] with an older generation does nothing,
 * so a service that is being replaced cannot end the run of its successor.
 */
class AppLog(
    private val sink: LogEventSink,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val device: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: (Long) -> UUID = { Uuid7.next(it) },
    private val logcat: EventLog = EventLog.Logcat,
    private val capacity: Int = QUEUE_CAPACITY,
    private val hourlyLimit: Int = HOURLY_LIMIT,
) : EventLog {
    private class Pending(
        val atMs: Long,
        val priority: Int,
        val tag: String,
        val message: String,
    )

    private class Run(
        val generation: Long,
        val queue: Channel<Pending>,
        val writer: Job,
    )

    private val lock = Mutex()
    private var generation = 0L

    @Volatile
    private var run: Run? = null

    /** Lines the queue threw away since the writer last said so. */
    private val dropped = AtomicInteger()

    // Only the writer coroutine touches these; runs never overlap.
    private var hour = -1L
    private var written = 0

    override fun log(
        priority: Int,
        tag: String,
        message: String,
    ) {
        logcat.log(priority, tag, message)
        run?.queue?.trySend(Pending(now(), priority, tag, message.take(MAX_MESSAGE)))
    }

    /**
     * Starts keeping lines; the capture service calls it when it begins. A run still open is finished first.
     * Returns the generation to hand to [stop].
     */
    suspend fun start(): Long =
        lock.withLock {
            run?.let { finish(it) }
            val queue = Channel<Pending>(capacity, BufferOverflow.DROP_OLDEST) { dropped.incrementAndGet() }
            val writer =
                scope.launch(io) {
                    for (line in queue) {
                        write(line)
                        reportDrops()
                    }
                }
            Run(++generation, queue, writer).also { run = it }.generation
        }

    /** Ends the run [generation] names and writes the lines still queued; any other generation is ignored. */
    suspend fun stop(generation: Long) =
        lock.withLock {
            val current = run ?: return@withLock
            if (current.generation != generation) return@withLock
            finish(current)
        }

    /** Closes the queue and lets the writer empty it, so the last lines of a run are not lost. */
    private suspend fun finish(current: Run) {
        run = null
        current.queue.close()
        current.writer.join()
    }

    private suspend fun reportDrops() {
        val count = dropped.getAndSet(0)
        if (count > 0) write(Pending(now(), Log.WARN, TAG, "dropped $count log lines"))
    }

    // Nothing here may crash the process or the service's shutdown: a missed line is only a gap in the log.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(line: Pending) {
        try {
            val atMs = line.atMs
            if (withinLimit(atMs)) {
                store(atMs, line.priority, line.tag, line.message)
            } else if (written == hourlyLimit) {
                written++
                store(
                    atMs,
                    Log.WARN,
                    TAG,
                    "log rate limit hit: more than $hourlyLimit lines this hour, dropping the rest",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat.log(Log.WARN, TAG, "The diagnostics log refused a line: ${e.javaClass.simpleName}")
        }
    }

    /** Counts the line against the hour it belongs to; false once the hour's allowance is used up. */
    private fun withinLimit(atMs: Long): Boolean {
        val current = atMs / HOUR_MS
        if (current != hour) {
            hour = current
            written = 0
        }
        if (written >= hourlyLimit) return false
        written++
        return true
    }

    private suspend fun store(
        atMs: Long,
        priority: Int,
        tag: String,
        message: String,
    ) = sink.addLog(
        DiagnosticLogEvent(
            id = newId(atMs).toString(),
            at = Instant.ofEpochMilli(atMs).toString(),
            kind = DiagnosticLogEvent.KIND,
            level = levelWord(priority),
            tag = tag,
            message = message,
            appVersion = appVersion,
            device = device,
        ),
    )

    companion object {
        const val QUEUE_CAPACITY = 512
        const val HOURLY_LIMIT = 2000
        const val MAX_MESSAGE = 1000
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val TAG = "AppLog"

        fun levelWord(priority: Int): String =
            when (priority) {
                Log.ERROR, Log.ASSERT -> "E"
                Log.WARN -> "W"
                Log.INFO -> "I"
                else -> "D"
            }
    }
}
