package io.github.nytka_app.capture

import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Plays [chime] every `consentChimeMinutes` while the pendant records and voice enrollment does not read it. The
 * first chime comes at once, unless one played less than that long ago: a flapping link does not chime on each
 * reconnect, and a changed interval counts from the last chime. [clock] is milliseconds on a clock that does not
 * jump with the wall time.
 */
class ConsentChime(
    private val status: Flow<CaptureStatus>,
    private val enrolling: Flow<Boolean>,
    private val settings: Flow<Settings>,
    private val chime: Chime,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private var lastChimeMs: Long? = null

    fun start(): Job =
        scope.launch {
            val recording = status.map { it.recording }.distinctUntilChanged()
            val wanted = settings.map { it.consentChime to it.consentChimeMinutes }.distinctUntilChanged()
            combine(recording, enrolling, wanted) { rec, enrol, (on, minutes) ->
                if (rec &&
                    !enrol &&
                    on
                ) {
                    minutes
                } else {
                    null
                }
            }.distinctUntilChanged()
                .collectLatest { minutes -> if (minutes != null) chimeEvery(minutes) }
        }

    private suspend fun chimeEvery(minutes: Int) {
        val interval = minutes * MS_PER_MINUTE
        while (true) {
            val last = lastChimeMs
            if (last != null) delay((last + interval - clock()).coerceAtLeast(0))
            chime.play()
            lastChimeMs = clock()
        }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
