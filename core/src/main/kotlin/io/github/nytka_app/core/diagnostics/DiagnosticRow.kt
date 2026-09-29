package io.github.nytka_app.core.diagnostics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A sample as stored: the wire JSON as it was taken, so the upload sends exactly what the export shows. */
@Entity(tableName = "diagnostic_samples")
data class DiagnosticRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    val json: String,
    val uploaded: Boolean = false,
)
