package io.github.nytka_app.briefs

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Nudge
import io.github.nytka_app.core.api.NudgesClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.SettingsSource
import java.time.Clock

/** Posts one nudge; false when it could not (no notification permission), so it is tried again next run. */
fun interface NudgePoster {
    fun post(nudge: Nudge): Boolean
}

/**
 * The nudge half of the brief job. Asks only a server that lists `nudges`, posts each nudge id once, remembers the
 * ids in settings (only those still listed, at most [MAX_KEPT]), and does nothing on a failure.
 */
class NudgePoll(
    private val client: NudgesClient,
    private val info: InfoClient,
    private val settings: SettingsSource,
    private val poster: NudgePoster,
    private val clock: Clock,
) {
    suspend fun run() {
        val before = settings.current()
        if (!before.nudgeNotifications) return
        val server = (info.info() as? ApiResult.Ok)?.value ?: return
        if (!server.has(ServerInfo.FEATURE_NUDGES)) return
        val listed = (client.recent(clock.zone.id) as? ApiResult.Ok)?.value ?: return
        val posted =
            listed
                .filter { it.id !in before.notifiedNudges }
                .filter(poster::post)
                .map { it.id }
        val listedIds = listed.map { it.id }.toSet()
        val kept = (before.notifiedNudges.filter { it in listedIds } + posted).takeLast(MAX_KEPT)
        settings.update { it.copy(notifiedNudges = kept) }
    }

    companion object {
        const val MAX_KEPT = 50
    }
}
