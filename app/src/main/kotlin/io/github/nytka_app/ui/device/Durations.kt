package io.github.nytka_app.ui.device

private const val PACKET_MS = 80L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L

/** Audio held in [packets] ring packets: "12 min", "2 h 5 min", "under a minute". */
fun packetsToDuration(packets: Long): String = minutesText((packets * PACKET_MS + MS_PER_MINUTE / 2) / MS_PER_MINUTE)

private fun minutesText(minutes: Long): String =
    when {
        minutes < 1 -> "under a minute"
        minutes < MINUTES_PER_HOUR -> "$minutes min"
        minutes % MINUTES_PER_HOUR == 0L -> "${minutes / MINUTES_PER_HOUR} h"
        else -> "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"
    }

/** "45 s", "3 min": a span of [seconds] for the card. */
fun secondsText(seconds: Long): String =
    if (seconds < SECONDS_PER_MINUTE) {
        "$seconds s"
    } else {
        minutesText((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
    }

/** The spec's "in 2 min" for a retry delay. */
fun delayText(ms: Long): String = secondsText((ms + 500) / 1_000)
