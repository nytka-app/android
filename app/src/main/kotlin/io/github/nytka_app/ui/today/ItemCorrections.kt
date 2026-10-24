package io.github.nytka_app.ui.today

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Evidence
import io.github.nytka_app.core.api.ItemMarksClient
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.ui.itemNotice

/** What a correction left for the screen to say: done (with Undo for [undoable]) or refused with [failure]. */
data class CorrectionNotice(
    val kind: CorrectionKind,
    val undoable: LifeItem? = null,
    val failure: String? = null,
)

enum class CorrectionKind { NotImportant, NotMe, OtherPerson, Undone }

/**
 * The inline corrections Today and the Timeline share, all through the server's manual marks ([ItemMarksClient]).
 * A line mark changes what the server derives from the line, so the caller reloads after one.
 */
class ItemCorrections(
    private val marks: ItemMarksClient,
) {
    /** Marks [item] noise; the server keeps the wearer's mark over the model's. */
    suspend fun notImportant(item: LifeItem): CorrectionNotice =
        when (val result = marks.setSalience(item.kind, item.id, Salience.NOISE)) {
            is ApiResult.Ok -> CorrectionNotice(CorrectionKind.NotImportant, undoable = item)
            is ApiResult.Failure -> CorrectionNotice(CorrectionKind.NotImportant, failure = result.itemNotice())
        }

    /** Puts back what [item] had before "not important": its own mark if it had one, else the model's. */
    suspend fun undo(item: LifeItem): CorrectionNotice {
        val previous = item.salience.takeIf { item.salienceManual }
        return when (val result = marks.setSalience(item.kind, item.id, previous)) {
            is ApiResult.Ok -> CorrectionNotice(CorrectionKind.Undone)
            is ApiResult.Failure -> CorrectionNotice(CorrectionKind.Undone, failure = result.itemNotice())
        }
    }

    /** The line is not the wearer's. */
    suspend fun notMe(line: Evidence): CorrectionNotice =
        when (val result = marks.markLine(line.segmentId, false)) {
            is ApiResult.Ok -> CorrectionNotice(CorrectionKind.NotMe)
            is ApiResult.Failure -> CorrectionNotice(CorrectionKind.NotMe, failure = result.itemNotice())
        }

    /** The line is [personId]'s; a line the server gave the wearer stops being theirs first. */
    suspend fun otherPerson(
        line: Evidence,
        personId: String,
    ): CorrectionNotice {
        if (line.isUser == true) {
            val cleared = marks.markLine(line.segmentId, false)
            if (cleared is ApiResult.Failure) {
                return CorrectionNotice(
                    CorrectionKind.OtherPerson,
                    failure = cleared.itemNotice(),
                )
            }
        }
        return when (val result = marks.setLinePerson(line.segmentId, personId)) {
            is ApiResult.Ok -> CorrectionNotice(CorrectionKind.OtherPerson)
            is ApiResult.Failure -> CorrectionNotice(CorrectionKind.OtherPerson, failure = result.itemNotice())
        }
    }
}
