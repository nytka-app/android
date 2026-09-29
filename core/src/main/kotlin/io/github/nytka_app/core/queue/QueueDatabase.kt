package io.github.nytka_app.core.queue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QueuedFrame::class, SealedChunk::class], version = 1)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao

    companion object {
        fun open(
            context: Context,
            name: String = "queue.db",
        ): QueueDatabase = Room.databaseBuilder(context, QueueDatabase::class.java, name).build()
    }
}
