package io.github.nytka_app.core.transcribe

import io.github.nytka_app.core.queue.QueueDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies each chunk the queue seals into `draft_audio` while "Transcribe on this phone" is on. A chunk leaves the
 * queue about 30 s after it is sealed, long before the phone is next on its charger, so the transcriber needs its own
 * copy. A SQLite trigger takes it in the transaction that inserts the chunk: `FrameQueue`, the uploader and every
 * rule about when a chunk is deleted stay exactly as they are. The trigger keeps the newest [MAX_COPIES] copies
 * (about 16 hours of speech) and drops older ones; a draft is a convenience, the server's transcript is the record.
 */
class DraftAudioTap(
    private val database: QueueDatabase,
) {
    private val dao = database.drafts()

    /** Creates or drops the trigger; switching off also deletes the copies still waiting. */
    suspend fun sync(on: Boolean) =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            if (on) {
                db.execSQL(CREATE_TRIGGER)
            } else {
                db.execSQL("drop trigger if exists $TRIGGER")
                dao.deleteAllAudio()
            }
        }

    companion object {
        const val TRIGGER = "draft_audio_copy"
        const val MAX_COPIES = 2000

        private const val CREATE_TRIGGER =
            "create trigger if not exists $TRIGGER after insert on chunks begin " +
                "insert or ignore into draft_audio (chunkId, session, firstSeq, frameCount, body, createdAtMs) " +
                "values (new.id, new.session, new.firstSeq, new.frameCount, new.body, new.createdAtMs); " +
                "delete from draft_audio where id < " +
                "(select id from draft_audio order by id desc limit 1 offset ${MAX_COPIES - 1}); " +
                "end"
    }
}
