package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/**
 * One thing a conversation left, as Today and the Timeline show it (`today`): a task, a memory or a note, with the
 * lines it was taken from. [at] is when it was said. Today never carries an item with a [sensitivity].
 */
@Serializable
data class LifeItem(
    val kind: String,
    val id: String,
    val text: String,
    val salience: String? = null,
    /** True while the wearer's own mark on [salience] stands. */
    val salienceManual: Boolean = false,
    val sensitivity: String? = null,
    val at: String? = null,
    val conversationId: String? = null,
    val personId: String? = null,
    val personName: String? = null,
    val due: String? = null,
    val done: Boolean = false,
    /** Only on `GET /today`: [DayPart] by [at] in the zone asked for. */
    val part: String? = null,
    /** The cited lines, at most 3, oldest first; empty for an item added by hand. */
    val evidence: List<Evidence> = emptyList(),
) {
    companion object {
        const val KIND_TASK = "task"
        const val KIND_MEMORY = "memory"
        const val KIND_NOTE = "note"
    }
}

/** A line an item cites. [speaker] is the server's label ("Wearer", a name, "Voice 2", "Unknown"), never a raw one. */
@Serializable
data class Evidence(
    val segmentId: Long,
    val conversationId: String,
    val at: String,
    val quote: String,
    val speaker: String? = null,
    val isUser: Boolean? = null,
    val personId: String? = null,
    /** How sure the server is that the line is the wearer's, 0 to 1; null when nothing decided. */
    val wearerConfidence: Double? = null,
)

object Salience {
    const val VITAL = "vital"
    const val ORDINARY = "ordinary"
    const val NOISE = "noise"
}

object DayPart {
    const val MORNING = "morning"
    const val DAY = "day"
    const val EVENING = "evening"
}

/** `GET /today`: at most 7 vital items of [date]. */
@Serializable
data class TodayPage(
    val date: String,
    val items: List<LifeItem> = emptyList(),
)

/** `GET /timeline`: the scenes of [date], oldest first. */
@Serializable
data class Timeline(
    val date: String,
    val scenes: List<Scene> = emptyList(),
)

/** A span of the day: one conversation, or one topic of it. A [noise] scene is a fragment the app folds away. */
@Serializable
data class Scene(
    val id: String,
    val conversationId: String,
    val startedAt: String,
    val endedAt: String,
    val title: String? = null,
    val summary: String? = null,
    val people: List<ScenePerson> = emptyList(),
    val place: String? = null,
    val noise: Boolean = false,
    val sensitivity: String? = null,
    val items: List<LifeItem> = emptyList(),
)

/** Someone heard in a scene, the wearer left out; [personId] is null for a voice nobody has named. */
@Serializable
data class ScenePerson(
    val name: String,
    val personId: String? = null,
)

/** `GET /insights` (`insights`): what the server can say about the wearer over time, and what it still needs. */
@Serializable
data class Insights(
    val daysWithAudio: Int = 0,
    val trustedAfterDays: Int = 0,
    val insights: List<Insight> = emptyList(),
)

/** A locked insight carries [progress] and nothing to show; an unlocked one its [summary] and maybe a [series]. */
@Serializable
data class Insight(
    val id: String,
    val title: String,
    val kind: String? = null,
    val unlocked: Boolean = false,
    val progress: InsightProgress? = null,
    val summary: String? = null,
    val series: List<InsightPoint> = emptyList(),
    val baseline: Double? = null,
    val unitLabel: String? = null,
)

@Serializable
data class InsightProgress(
    val have: Int,
    val need: Int,
    val unit: String = "days",
)

@Serializable
data class InsightPoint(
    val date: String,
    val value: Double,
)
