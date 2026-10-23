package io.github.nytka_app.core.context

/** What a player reports it is for; only [MEDIA], [GAME] and [UNKNOWN] count as sound a room can hear. */
enum class Usage { MEDIA, GAME, UNKNOWN, OTHER }

enum class Mode { NORMAL, IN_CALL, IN_COMMUNICATION, CALL_REDIRECT, COMMUNICATION_REDIRECT, OTHER }

enum class RangeKind(
    val wire: String,
) {
    MEDIA("media"),
    CALL("call"),
    SCREEN("screen"),
    HEADPHONES("headphones"),
    ACTIVITY("activity"),
    NOW_PLAYING("now-playing"),
    GAIN("gain"),
}

/** What Android's activity transitions say the phone's owner is doing; sent as a range's detail. */
enum class Activity(
    val wire: String,
) {
    STILL("still"),
    WALKING("walking"),
    RUNNING("running"),
    CYCLING("cycling"),
    VEHICLE("vehicle"),
}

enum class Route(
    val wire: String,
) {
    SPEAKER("speaker"),
    EARPIECE("earpiece"),
    HEADSET("headset"),
    BLUETOOTH("bluetooth"),
    OTHER("other"),
}

/**
 * A stretch of time with sound from the phone, its screen on, or its sound going to headphones: kind, route and two
 * times, never what played or who called.
 */
data class ContextRange(
    val kind: RangeKind,
    val route: Route,
    val startMs: Long,
    val endMs: Long,
    /**
     * One word from a fixed list for [RangeKind.ACTIVITY] and [RangeKind.NOW_PLAYING], the level for [RangeKind.GAIN];
     * null for the rest.
     */
    val detail: String? = null,
)

/**
 * Turns the phone's audio events into closed [ContextRange]s. Pure: the caller feeds events and a clock, and calls
 * [onTimer] at [pendingCloseAtMs] so a pause that outlasts the grace ends the range without a further event.
 * Not thread-safe by itself; the recorder calls it from one thread.
 */
class ContextRangeTracker(
    private val clock: () -> Long,
    private val emit: (ContextRange) -> Unit,
) {
    private var mediaStart: Long? = null

    /** When the sound stopped, while the range waits out the grace; null while it plays or none is open. */
    private var mediaStoppedAt: Long? = null

    private var callStart: Long? = null
    private var route = Route.OTHER
    private var routeSince = 0L
    private val held = mutableMapOf<Route, Long>()
    private var screenStart: Long? = null
    private var headphones: Route? = null
    private var headphonesStart = 0L
    private var activity: Activity? = null
    private var activityStart = 0L
    private var playing: Pair<MediaCategory, Route>? = null
    private var playingStart = 0L

    /** The time [onTimer] should run at, or null when no range waits to close. */
    val pendingCloseAtMs: Long? get() = mediaStoppedAt?.plus(MEDIA_GRACE_MS)

    fun onPlayback(
        activeUsages: Set<Usage>,
        mediaOnSpeaker: Boolean,
        musicVolume: Int,
    ) {
        val now = clock()
        settle(now)
        val audible = activeUsages.any { it == Usage.MEDIA || it == Usage.GAME || it == Usage.UNKNOWN }
        val outOnSpeaker = mediaOnSpeaker && musicVolume > 0
        val start = mediaStart
        when {
            audible && outOnSpeaker -> {
                mediaStoppedAt = null
                if (start == null) mediaStart = now
            }

            start == null -> Unit

            // The route or the volume changed: the room no longer hears it, so there is no grace.
            audible -> closeMedia(now)

            mediaStoppedAt == null -> mediaStoppedAt = now
        }
    }

    fun onMode(mode: Mode) {
        val now = clock()
        settle(now)
        val inCall = mode.isCall()
        val start = callStart
        when {
            inCall && start == null -> {
                callStart = now
                held.clear()
                routeSince = now
            }

            !inCall && start != null -> closeCall(start, now)
        }
    }

    fun onCommunicationDevice(next: Route) {
        val now = clock()
        settle(now)
        if (callStart != null) held.merge(route, now - routeSince, Long::plus)
        route = next
        routeSince = now
    }

    fun onScreen(on: Boolean) {
        val now = clock()
        settle(now)
        val start = screenStart
        when {
            on && start == null -> screenStart = now
            !on && start != null -> closeScreen(start, now)
        }
    }

    /** Where media plays when it goes to headphones, [Route.HEADSET] or [Route.BLUETOOTH]; null when it does not. */
    fun onHeadphones(next: Route?) {
        val now = clock()
        settle(now)
        if (next == headphones) return
        closeHeadphones(now)
        headphones = next
        headphonesStart = now
    }

    /**
     * The activity Android reports as entered at [atMs] (on the tracker's clock), or null when it reports leaving
     * [current] or the switch goes off. A transition dated before the open range began counts from that start.
     */
    fun onActivity(
        next: Activity?,
        atMs: Long = clock(),
    ) {
        settle(clock())
        if (next == activity) return
        val at = maxOf(atMs, activityStart)
        activity?.let { close(RangeKind.ACTIVITY, Route.OTHER, activityStart, at, it.wire) }
        activity = next
        activityStart = at
    }

    /** What a media session plays and where, or null when nothing plays or the switch goes off. */
    fun onNowPlaying(
        category: MediaCategory?,
        route: Route,
    ) {
        val now = clock()
        settle(now)
        val next = category?.let { it to route }
        if (next == playing) return
        closeNowPlaying(now)
        playing = next
        playingStart = now
    }

    /** Closes a media range whose grace has run out. */
    fun onTimer() = settle(clock())

    /** Capture stops: whatever is open ends now, and a range that was waiting ends where the sound stopped. */
    fun onStop() {
        val now = clock()
        mediaStart?.let { close(RangeKind.MEDIA, Route.SPEAKER, it, mediaStoppedAt ?: now) }
        mediaStart = null
        mediaStoppedAt = null
        callStart?.let { closeCall(it, now) }
        screenStart?.let { closeScreen(it, now) }
        closeHeadphones(now)
        headphones = null
        onActivity(null, now)
        closeNowPlaying(now)
        playing = null
    }

    private fun settle(now: Long) {
        val stopped = mediaStoppedAt ?: return
        if (now - stopped >= MEDIA_GRACE_MS) {
            mediaStart?.let { close(RangeKind.MEDIA, Route.SPEAKER, it, stopped) }
            mediaStart = null
            mediaStoppedAt = null
        }
    }

    private fun closeMedia(now: Long) {
        mediaStart?.let { close(RangeKind.MEDIA, Route.SPEAKER, it, now) }
        mediaStart = null
        mediaStoppedAt = null
    }

    private fun closeCall(
        start: Long,
        now: Long,
    ) {
        held.merge(route, now - routeSince, Long::plus)
        val longest = held.maxByOrNull { it.value }?.key ?: route
        callStart = null
        held.clear()
        close(RangeKind.CALL, longest, start, now)
    }

    private fun closeScreen(
        start: Long,
        now: Long,
    ) {
        screenStart = null
        close(RangeKind.SCREEN, Route.OTHER, start, now)
    }

    private fun closeHeadphones(now: Long) {
        headphones?.let { close(RangeKind.HEADPHONES, it, headphonesStart, now) }
    }

    private fun closeNowPlaying(now: Long) {
        playing?.let { (category, route) -> close(RangeKind.NOW_PLAYING, route, playingStart, now, category.wire) }
    }

    private fun close(
        kind: RangeKind,
        route: Route,
        start: Long,
        end: Long,
        detail: String? = null,
    ) {
        val cut = minOf(end, start + MAX_MS)
        if (cut - start >= MIN_MS) emit(ContextRange(kind, route, start, cut, detail))
    }

    private fun Mode.isCall() =
        this == Mode.IN_CALL ||
            this == Mode.IN_COMMUNICATION ||
            this == Mode.CALL_REDIRECT ||
            this == Mode.COMMUNICATION_REDIRECT

    companion object {
        const val MEDIA_GRACE_MS = 2_000L
        const val MIN_MS = 3_000L
        const val MAX_MS = 12 * 60 * 60 * 1_000L
    }
}
