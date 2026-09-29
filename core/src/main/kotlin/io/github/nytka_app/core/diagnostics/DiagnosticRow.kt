package io.github.nytka_app.core.diagnostics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A sample or a log event as stored: the wire JSON as it was taken, so the upload sends exactly what the
 * export shows. [kind] is `sample` or `log`; rows from before version 3 are samples.
 */
@Entity(tableName = "diagnostic_samples")
data class DiagnosticRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    val json: String,
    val uploaded: Boolean = false,
    @ColumnInfo(defaultValue = KIND_SAMPLE) val kind: String = KIND_SAMPLE,
)

const val KIND_SAMPLE = "sample"
