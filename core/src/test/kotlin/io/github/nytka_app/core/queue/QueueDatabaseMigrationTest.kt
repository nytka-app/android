package io.github.nytka_app.core.queue

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            QueueDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `version 1 to 2 keeps the queue and adds the diagnostics table`() {
        helper.createDatabase(NAME, 1).use { db ->
            db.execSQL(
                "insert into frames (id, session, seq, capturedAtMs, payload) values (1, 's', 0, 5, x'0102')",
            )
            db.execSQL(
                "insert into chunks (id, session, firstSeq, frameCount, createdAtMs, body) " +
                    "values (1, 's', 0, 1, 5, x'0304')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 2, true).use { db ->
            db.query("select count(*), sum(length(payload)) from frames").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(2, it.getInt(1))
            }
            db.query("select count(*), sum(length(body)) from chunks").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(2, it.getInt(1))
            }
            db.execSQL("insert into diagnostic_samples (id, at_ms, json, uploaded) values ('a', 1, '{}', 0)")
            db.query("select count(*) from diagnostic_samples").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun `version 2 to 3 marks the samples already stored as samples`() {
        helper.createDatabase(NAME, 2).use { db ->
            db.execSQL("insert into diagnostic_samples (id, at_ms, json, uploaded) values ('a', 1, '{}', 0)")
        }

        helper.runMigrationsAndValidate(NAME, 3, true).use { db ->
            db.query("select kind from diagnostic_samples where id = 'a'").use {
                it.moveToFirst()
                assertEquals("sample", it.getString(0))
            }
        }
    }

    @Test
    fun `version 3 to 4 keeps the queue as live data and adds the ring sync tables`() {
        helper.createDatabase(NAME, 3).use { db ->
            db.execSQL(
                "insert into frames (id, session, seq, capturedAtMs, payload) values (1, 's', 0, 5, x'0102')",
            )
            db.execSQL(
                "insert into chunks (id, session, firstSeq, frameCount, createdAtMs, body) " +
                    "values (1, 's', 0, 1, 5, x'0304')",
            )
            db.execSQL(
                "insert into diagnostic_samples (id, at_ms, json, uploaded, kind) values ('a', 1, '{}', 0, 'sample')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 4, true).use { db ->
            db.query("select stored, ringSeq, epoch, length(payload) from frames where id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertTrue(it.isNull(1))
                assertEquals(0, it.getInt(2))
                assertEquals(2, it.getInt(3))
            }
            db.query("select stored, ringFirst, ringLast, epoch, length(body) from chunks where id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertEquals(0, it.getInt(3))
                assertEquals(2, it.getInt(4))
            }
            db.query("select count(*) from diagnostic_samples").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            db.execSQL(
                "insert into ring_position (id, pendant, committedNext, epoch, advanced, session, nextFrame, " +
                    "lastDropped, lastStampS, clockWriteSeq, clockSkewS, staleClock) " +
                    "values (1, 'p', 9, 0, 0, null, 0, 0, null, null, null, 0)",
            )
            db.execSQL("insert into mute_log (atMs, muted) values (5, 1)")
            db.execSQL(
                "insert into parked_chunks (session, firstSeq, frameCount, createdAtMs, body, ringFirst, ringLast, " +
                    "epoch, code, reason, parkedAtMs) values ('s', 0, 1, 5, x'00', 1, 2, 0, 409, 'r', 6)",
            )
            listOf("ring_position", "mute_log", "parked_chunks").forEach { table ->
                db.query("select count(*) from $table").use {
                    it.moveToFirst()
                    assertEquals(1, it.getInt(0))
                }
            }
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
