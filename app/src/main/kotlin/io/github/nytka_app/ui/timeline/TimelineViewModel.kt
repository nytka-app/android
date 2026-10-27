package io.github.nytka_app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayEvent
import io.github.nytka_app.core.api.EventsClient
import io.github.nytka_app.core.api.Evidence
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ItemMarksClient
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.Scene
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.TodayClient
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.today.CorrectionNotice
import io.github.nytka_app.ui.today.ItemCorrections
import io.github.nytka_app.ui.unreachable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A row of the day: a scene, a run of fragments folded into one line, or a quiet line for a day event. */
sealed interface TimelineRow {
    data class Of(
        val scene: Scene,
    ) : TimelineRow

    data class Fragments(
        val scenes: List<Scene>,
    ) : TimelineRow

    data class Event(
        val event: DayEvent,
    ) : TimelineRow
}

data class TimelineUiState(
    val date: LocalDate,
    val today: LocalDate,
    val rows: List<TimelineRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val notice: CorrectionNotice? = null,
) {
    val canGoForward: Boolean get() = date < today

    /** Loaded, reachable, and no scenes: the screen says what will appear. */
    val nothingHeard: Boolean get() = rows.isEmpty() && !loading && error == null && !offline
}

/**
 * The Timeline: one day as scenes with their items, from today back; a scene opens its conversation. On a server that
 * lists `events`, the day's events sit between the scenes by time; they come after the scenes and never hold them back.
 */
@HiltViewModel
class TimelineViewModel
    @Inject
    constructor(
        private val api: TodayClient,
        marks: ItemMarksClient,
        private val events: EventsClient,
        private val info: InfoClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val corrections = ItemCorrections(marks)
        private val mutableState = MutableStateFlow(LocalDate.now(clock).let { TimelineUiState(date = it, today = it) })
        val state: StateFlow<TimelineUiState> = mutableState.asStateFlow()
        val zone: ZoneId get() = clock.zone
        private var loadJob: Job? = null

        init {
            load()
        }

        fun previousDay() = show(mutableState.value.date.minusDays(1))

        fun nextDay() {
            if (mutableState.value.canGoForward) show(mutableState.value.date.plusDays(1))
        }

        fun showToday() = show(LocalDate.now(clock))

        fun refresh() = load()

        private fun show(date: LocalDate) {
            mutableState.update {
                it.copy(
                    date = date,
                    today = LocalDate.now(clock),
                    rows = emptyList(),
                    loading = true,
                )
            }
            load()
        }

        private fun load() {
            loadJob?.cancel()
            val date = mutableState.value.date
            loadJob =
                viewModelScope.launch {
                    when (val result = api.timeline(date.toString(), clock.zone.id)) {
                        is ApiResult.Ok -> {
                            mutableState.update {
                                it.copy(
                                    rows = rows(result.value.scenes),
                                    loading = false,
                                    error = null,
                                    offline = false,
                                )
                            }
                            loadEvents(date)
                        }

                        is ApiResult.Failure ->
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    offline = result.kind.unreachable,
                                    error = result.notice().takeUnless { result.kind.unreachable },
                                )
                            }
                    }
                }
        }

        /** Day events are extra: a server without them, or a failed call, leaves the scenes as they are. */
        private suspend fun loadEvents(date: LocalDate) {
            val server = info.info()
            if (server !is ApiResult.Ok || !server.value.has(ServerInfo.FEATURE_EVENTS)) return
            val day = events.events(date.toString(), clock.zone.id) as? ApiResult.Ok ?: return
            mutableState.update { it.copy(rows = withEvents(it.rows, day.value.events)) }
        }

        /** The item stays on the Timeline, now as noise; a refusal says so. */
        fun notImportant(item: LifeItem) =
            viewModelScope.launch {
                val notice = corrections.notImportant(item)
                mutableState.update { it.copy(notice = notice) }
                if (notice.failure == null) {
                    mutableState.update { state -> state.copy(rows = state.rows.map { marked(it, item) }) }
                }
            }

        fun undo(item: LifeItem) =
            viewModelScope.launch {
                mutableState.update { it.copy(notice = corrections.undo(item)) }
                load()
            }

        fun notMe(line: Evidence) =
            viewModelScope.launch {
                mutableState.update { it.copy(notice = corrections.notMe(line)) }
                load()
            }

        fun otherPerson(
            line: Evidence,
            personId: String,
        ) = viewModelScope.launch {
            mutableState.update { it.copy(notice = corrections.otherPerson(line, personId)) }
            load()
        }

        fun noticeShown() = mutableState.update { it.copy(notice = null) }

        companion object {
            /** Scenes oldest first; consecutive noise scenes fold into one [TimelineRow.Fragments]. */
            fun rows(scenes: List<Scene>): List<TimelineRow> {
                val rows = mutableListOf<TimelineRow>()
                var run = mutableListOf<Scene>()

                fun flush() {
                    if (run.isNotEmpty()) rows += TimelineRow.Fragments(run)
                    run = mutableListOf()
                }
                scenes.forEach { scene ->
                    if (scene.noise) {
                        run += scene
                    } else {
                        flush()
                        rows += TimelineRow.Of(scene)
                    }
                }
                flush()
                return rows
            }

            /**
             * [events] among [rows] by time, an event before a row that starts with it. Unknown kinds and events
             * tied to a private scene are dropped, so a name never shows past "Private".
             */
            fun withEvents(
                rows: List<TimelineRow>,
                events: List<DayEvent>,
            ): List<TimelineRow> {
                val scenes =
                    rows.flatMap {
                        if (it is TimelineRow.Of) listOf(it.scene) else (it as? TimelineRow.Fragments)?.scenes.orEmpty()
                    }
                val private = scenes.filter { it.sensitivity != null }.map { it.conversationId }.toSet()
                val pending =
                    events
                        .filter { it.kind in DayEvent.KINDS && it.conversationId !in private }
                        .mapNotNull { event -> Formatting.parse(event.at)?.let { event to it } }
                        .sortedBy { it.second }
                        .toMutableList()
                val merged = mutableListOf<TimelineRow>()
                rows.filterNot { it is TimelineRow.Event }.forEach { row ->
                    val start = startOf(row)
                    while (start != null && pending.isNotEmpty() && pending.first().second <= start) {
                        merged += TimelineRow.Event(pending.removeAt(0).first)
                    }
                    merged += row
                }
                pending.forEach { merged += TimelineRow.Event(it.first) }
                return merged
            }

            private fun startOf(row: TimelineRow) =
                when (row) {
                    is TimelineRow.Of -> Formatting.parse(row.scene.startedAt)
                    is TimelineRow.Fragments -> Formatting.parse(row.scenes.first().startedAt)
                    is TimelineRow.Event -> null
                }

            private fun marked(
                row: TimelineRow,
                item: LifeItem,
            ): TimelineRow =
                when (row) {
                    is TimelineRow.Fragments, is TimelineRow.Event -> row
                    is TimelineRow.Of ->
                        TimelineRow.Of(
                            row.scene.copy(
                                items =
                                    row.scene.items.map {
                                        if (it.kind == item.kind && it.id == item.id) {
                                            it.copy(salience = Salience.NOISE, salienceManual = true)
                                        } else {
                                            it
                                        }
                                    },
                            ),
                        )
                }
        }
    }
