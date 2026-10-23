package io.github.nytka_app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayPart
import io.github.nytka_app.core.api.Evidence
import io.github.nytka_app.core.api.ItemMarksClient
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.TodayClient
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.notice
import io.github.nytka_app.ui.unreachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/** The items of one part of the day, in the order said. */
data class TodaySection(
    val part: String,
    val items: List<LifeItem>,
)

data class TodayUiState(
    val sections: List<TodaySection> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val notice: CorrectionNotice? = null,
) {
    val empty: Boolean get() = sections.isEmpty()

    /** Loaded, reachable, and nothing vital: the screen says what will appear. */
    val nothingYet: Boolean get() = empty && !loading && error == null && !offline
}

/**
 * Today: at most [MAX_ITEMS] vital items, in morning, day and evening sections. The server already leaves out what is
 * not vital and anything sensitive; the app drops both again, so a server mistake never puts therapy or money on the
 * first screen.
 */
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val api: TodayClient,
        marks: ItemMarksClient,
        private val clock: Clock,
    ) : ViewModel() {
        private val corrections = ItemCorrections(marks)
        private val mutableState = MutableStateFlow(TodayUiState())
        val state: StateFlow<TodayUiState> = mutableState.asStateFlow()
        val zone: ZoneId get() = clock.zone

        init {
            load()
        }

        fun refresh() {
            mutableState.update { it.copy(refreshing = true) }
            load()
        }

        private fun load() {
            viewModelScope.launch {
                when (val result = api.today(clock.zone.id)) {
                    is ApiResult.Ok ->
                        mutableState.update {
                            it.copy(
                                sections = sections(result.value.items, clock.zone),
                                loading = false,
                                refreshing = false,
                                error = null,
                                offline = false,
                            )
                        }

                    is ApiResult.Failure ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                offline = result.kind.unreachable,
                                error = result.notice().takeUnless { result.kind.unreachable },
                            )
                        }
                }
            }
        }

        /** Takes [item] off Today at once; a refusal puts it back. */
        fun notImportant(item: LifeItem) {
            val before = mutableState.value.sections
            mutableState.update { it.copy(sections = without(before, item)) }
            viewModelScope.launch {
                val notice = corrections.notImportant(item)
                mutableState.update {
                    it.copy(
                        notice = notice,
                        sections =
                            if (notice.failure !=
                                null
                            ) {
                                before
                            } else {
                                it.sections
                            },
                    )
                }
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
            const val MAX_ITEMS = 7
            private val PARTS = listOf(DayPart.MORNING, DayPart.DAY, DayPart.EVENING)

            /** Vital, not sensitive, at most [MAX_ITEMS], grouped by the server's part or else by the hour said. */
            fun sections(
                items: List<LifeItem>,
                zone: ZoneId,
            ): List<TodaySection> {
                val shown =
                    items
                        .filter { it.salience == Salience.VITAL && it.sensitivity == null }
                        .take(MAX_ITEMS)
                        .groupBy { it.part?.takeIf(PARTS::contains) ?: partOf(it, zone) }
                return PARTS.mapNotNull { part ->
                    shown[part]?.sortedBy { item -> item.at?.let(Formatting::parse) }?.let { TodaySection(part, it) }
                }
            }

            /** Morning before 12:00, day to 18:00, evening after; an item without a time counts as the day's. */
            fun partOf(
                item: LifeItem,
                zone: ZoneId,
            ): String {
                val hour =
                    item.at
                        ?.let(Formatting::parse)
                        ?.atZone(zone)
                        ?.hour ?: return DayPart.DAY
                return when {
                    hour < NOON -> DayPart.MORNING
                    hour < EVENING_HOUR -> DayPart.DAY
                    else -> DayPart.EVENING
                }
            }

            private fun without(
                sections: List<TodaySection>,
                item: LifeItem,
            ): List<TodaySection> =
                sections
                    .map { section ->
                        section.copy(
                            items =
                                section.items.filterNot {
                                    it.kind == item.kind &&
                                        it.id == item.id
                                },
                        )
                    }.filter { it.items.isNotEmpty() }

            private const val NOON = 12
            private const val EVENING_HOUR = 18
        }
    }
