package io.github.nytka_app.core.queue

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val NAME = "migration-test"
    }
}
