package io.github.nytka_app.core.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The server's settings catalog, admin only. A v0.1 server answers [FailureKind.NotFound]. */
interface ServerSettingsClient {
    suspend fun settings(): ApiResult<List<ServerSetting>>

    /**
     * All or nothing. A null value restores the key's default; a bad value is [FailureKind.Invalid] with the
     * messages by key, a locked key or an API key is [FailureKind.Conflict].
     */
    suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>>
}

class ServerSettingsApi(
    private val api: NytkaApi,
) : ServerSettingsClient {
    override suspend fun settings(): ApiResult<List<ServerSetting>> =
        api.request("GET", "api/v1/settings") { api.json.decodeFromString<SettingsAnswer>(it).items }

    override suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>> {
        val body =
            buildJsonObject {
                put(
                    "values",
                    buildJsonObject {
                        values.forEach { (key, value) ->
                            put(key, value?.let(::JsonPrimitive) ?: JsonNull)
                        }
                    },
                )
            }
        return api.request("PATCH", "api/v1/settings", body = body.toString()) {
            api.json.decodeFromString<SettingsAnswer>(it).items
        }
    }
}
