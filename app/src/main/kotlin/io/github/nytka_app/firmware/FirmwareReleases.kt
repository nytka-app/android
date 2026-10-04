package io.github.nytka_app.firmware

import io.github.nytka_app.pendant.FirmwareStream
import io.github.nytka_app.pendant.FirmwareVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What asking for the newest firmware of a [FirmwareStream] came to. */
sealed interface ReleaseAnswer {
    data class Latest(
        val version: FirmwareVersion,
    ) : ReleaseAnswer

    /** GitHub answered, but names no published release of the stream, or answered with an error (a rate limit). */
    data object NoRelease : ReleaseAnswer

    /** No answer at all: offline, a timeout, a TLS failure. */
    data object Unreachable : ReleaseAnswer
}

fun interface FirmwareReleases {
    suspend fun latest(stream: FirmwareStream): ReleaseAnswer
}

/**
 * Omi's firmware releases on GitHub, with no token: the tags of the stream (`git/matching-refs`, because the
 * repository's release list is mostly desktop and phone builds), then the release of the highest tag, which must be
 * published and not a pre-release. At most [MAX_RELEASE_LOOKUPS] releases are looked up, so one check costs at most
 * four requests against the 60 an hour GitHub allows per address. Sends nothing but the request: no identifier, no
 * cookie, no pendant data beyond the stream's tag prefix.
 */
class GithubFirmwareReleases(
    private val client: OkHttpClient,
    private val base: HttpUrl = "https://api.github.com/".toHttpUrl(),
) : FirmwareReleases {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latest(stream: FirmwareStream): ReleaseAnswer =
        withContext(Dispatchers.IO) {
            try {
                find(stream)
            } catch (_: IOException) {
                ReleaseAnswer.Unreachable
            } catch (_: SerializationException) {
                ReleaseAnswer.NoRelease
            }
        }

    private fun find(stream: FirmwareStream): ReleaseAnswer {
        val refs =
            get("git/matching-refs/tags/${stream.tagPrefix}", "per_page" to "100")
                ?.let { json.decodeFromString<List<Ref>>(it) }
                ?: return ReleaseAnswer.NoRelease
        val candidates =
            refs
                .mapNotNull { ref -> stream.versionOf(ref.ref.removePrefix("refs/tags/"))?.let { it to ref.ref } }
                .sortedByDescending { it.first }
                .take(MAX_RELEASE_LOOKUPS)
        for ((version, ref) in candidates) {
            val release =
                get("releases/tags/${ref.removePrefix("refs/tags/")}")?.let { json.decodeFromString<Release>(it) }
            if (release != null && !release.draft && !release.prerelease) return ReleaseAnswer.Latest(version)
        }
        return ReleaseAnswer.NoRelease
    }

    /** The body of a 200; null for any other answer, which is no reason to look further. */
    private fun get(
        path: String,
        vararg query: Pair<String, String>,
    ): String? {
        val url =
            base
                .newBuilder()
                .addPathSegments("repos/$REPOSITORY/$path")
                .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
        client.newCall(request).execute().use { response ->
            return if (response.code == 200) response.body.string() else null
        }
    }

    @Serializable
    private data class Ref(
        val ref: String,
    )

    @Serializable
    private data class Release(
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    companion object {
        const val REPOSITORY = "BasedHardware/omi"
        const val MAX_RELEASE_LOOKUPS = 3
    }
}
