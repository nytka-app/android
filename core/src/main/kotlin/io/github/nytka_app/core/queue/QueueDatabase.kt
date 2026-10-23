package io.github.nytka_app.core.queue

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.nytka_app.core.diagnostics.DiagnosticRow
import io.github.nytka_app.core.diagnostics.DiagnosticsDao
import io.github.nytka_app.core.transcribe.DraftAudioRow
import io.github.nytka_app.core.transcribe.DraftTextRow
import io.github.nytka_app.core.transcribe.DraftsDao

// Version 2 adds the diagnostic samples, version 3 their kind (sample or log), version 4 the ring sync (stored
// frames and chunks, the position, parked chunks, the mute log), version 5 the bookmark outbox, version 6 the
// phone-context outbox, version 7 the phone's own transcription (audio copies and draft text), version 8 a detail on
// context ranges; version 1 held the queue only.
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
            BookmarkRow::class,
            ContextRow::class,
            DraftAudioRow::class,
            DraftTextRow::class,
        ],
    version = 8,
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            AutoMigration(from = 5, to = 6),
            AutoMigration(from = 6, to = 7),
            AutoMigration(from = 7, to = 8),
        ],
)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao

    abstract fun diagnostics(): DiagnosticsDao

    abstract fun bookmarks(): BookmarksDao

    abstract fun contextOutbox(): ContextOutboxDao

    abstract fun drafts(): DraftsDao

    companion object {
        fun open(
            context: Context,
            name: String = "queue.db",
        ): QueueDatabase = Room.databaseBuilder(context, QueueDatabase::class.java, name).build()
    }
}
