package io.github.nytka_app.core.queue

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.nytka_app.core.diagnostics.DiagnosticRow
import io.github.nytka_app.core.diagnostics.DiagnosticsDao

// Version 2 adds the diagnostic samples, version 3 their kind (sample or log), version 4 the ring sync (stored
// frames and chunks, the position, parked chunks, the mute log); version 1 held the queue only.
// Never a destructive fallback: the queue holds audio nobody can record again.
@Database(
    entities =
        [
            QueuedFrame::class,
            SealedChunk::class,
            DiagnosticRow::class,
            ParkedChunk::class,
            RingPositionRow::class,
            MuteLogRow::class,
        ],
    version = 4,
    autoMigrations =
        [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao

    abstract fun diagnostics(): DiagnosticsDao

    companion object {
        fun open(
            context: Context,
            name: String = "queue.db",
        ): QueueDatabase = Room.databaseBuilder(context, QueueDatabase::class.java, name).build()
    }
}
