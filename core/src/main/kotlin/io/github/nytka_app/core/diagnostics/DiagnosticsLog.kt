package io.github.nytka_app.core.diagnostics

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.Instant

/** Where the recorder puts samples. [DiagnosticsLog] in the app. */
interface DiagnosticsSink {
    suspend fun add(sample: DiagnosticSample)

    /** Forgets samples older than seven days. */
    suspend fun prune()
}

/** What the uploader, the export and the developer screen read. [DiagnosticsLog] in the app. */
interface DiagnosticsSource {
    val count: Flow<Int>

    suspend fun pending(limit: Int): List<DiagnosticRow>

    suspend fun markUploaded(ids: List<String>)

    /** A page of the last seven days, oldest first, so an export never holds them all in memory. */
    suspend fun recent(
        limit: Int,
        offset: Int,
    ): List<DiagnosticSample>
}

/** The samples of the last seven days, in Room next to the queue. */
class DiagnosticsLog(
    private val dao: DiagnosticsDao,
    private val now: () -> Long = System::currentTimeMillis,
) : DiagnosticsSink,
    DiagnosticsSource {
    private val json = Json { ignoreUnknownKeys = true }

    override val count: Flow<Int> = dao.count()

    override suspend fun add(sample: DiagnosticSample) {
        dao.insert(
            DiagnosticRow(
                id = sample.id,
                atMs = Instant.parse(sample.at).toEpochMilli(),
                json = json.encodeToString(DiagnosticSample.serializer(), sample),
            ),
        )
    }

    override suspend fun prune() = dao.deleteOlderThan(now() - RETENTION_MS)

    override suspend fun pending(limit: Int): List<DiagnosticRow> = dao.oldestNotUploaded(limit)

    override suspend fun markUploaded(ids: List<String>) = dao.markUploaded(ids)

    override suspend fun recent(
        limit: Int,
        offset: Int,
    ): List<DiagnosticSample> =
        dao.since(now() - RETENTION_MS, limit, offset).map {
            json.decodeFromString(DiagnosticSample.serializer(), it.json)
        }

    companion object {
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
