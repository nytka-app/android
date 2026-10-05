package io.github.nytka_app.core.api

/** Nytka's own voice models of other people; a server before voice grouping answers [FailureKind.NotFound]. */
interface VoiceprintsClient {
    /** Deletes every voice group, every person voiceprint and every pending voice match. Admin only. */
    suspend fun deleteAll(): ApiResult<Unit>
}

class VoiceprintsApi(
    private val api: NytkaApi,
) : VoiceprintsClient {
    override suspend fun deleteAll(): ApiResult<Unit> = api.request("DELETE", "api/v1/people/voiceprints") { }
}
