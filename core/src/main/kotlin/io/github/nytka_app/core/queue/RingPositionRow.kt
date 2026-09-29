package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.nytka_app.core.ring.ClockPair
import io.github.nytka_app.core.ring.RingPosition
import java.util.UUID

/** The sync position of the paired pendant: one row, [id] 1. See [RingPosition] for the fields. */
@Entity(tableName = "ring_position")
data class RingPositionRow(
    @PrimaryKey val id: Int = ROW_ID,
    val pendant: String,
    val committedNext: Long,
    val epoch: Long,
    val advanced: Long,
    val session: String?,
    val nextFrame: Long,
    val lastDropped: Long,
    val lastStampS: Long?,
    val clockWriteSeq: Long?,
    val clockSkewS: Long?,
    val staleClock: Boolean,
) {
    fun toPosition() =
        RingPosition(
            pendant = pendant,
            committedNext = committedNext,
            epoch = epoch,
            advanced = advanced,
            session = session?.let(UUID::fromString),
            nextFrame = nextFrame,
            lastDropped = lastDropped,
            lastStampS = lastStampS,
            clock = if (clockWriteSeq != null && clockSkewS != null) ClockPair(clockWriteSeq, clockSkewS) else null,
            staleClock = staleClock,
        )

    companion object {
        const val ROW_ID = 1

        fun of(position: RingPosition) =
            RingPositionRow(
                pendant = position.pendant,
                committedNext = position.committedNext,
                epoch = position.epoch,
                advanced = position.advanced,
                session = position.session?.toString(),
                nextFrame = position.nextFrame,
                lastDropped = position.lastDropped,
                lastStampS = position.lastStampS,
                clockWriteSeq = position.clock?.writeSeq,
                clockSkewS = position.clock?.skewS,
                staleClock = position.staleClock,
            )
    }
}
