package io.github.nytka_app.ui.developer.server

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Fake(
        var catalog: List<ServerSetting>,
    ) : ServerSettingsClient {
        var loadAnswer: ApiResult.Failure? = null
        var updateAnswer: ApiResult.Failure? = null
        val sent = mutableListOf<Map<String, String?>>()

        override suspend fun settings(): ApiResult<List<ServerSetting>> = loadAnswer ?: ApiResult.Ok(catalog)

        override suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>> {
            sent += values
            updateAnswer?.let { return it }
            catalog =
                catalog.map { setting ->
                    if (setting.key in values) setting.copy(value = values[setting.key], source = "db") else setting
                }
            return ApiResult.Ok(catalog)
        }
    }

    private val api =
        Fake(
            listOf(
                ServerSetting("stt.url", "url", "https://stt.example", source = "env", locked = true),
                ServerSetting("stt.apiKey", "secret", isSet = true, source = "env", locked = true),
                ServerSetting("llm.model", "string", "gpt-x", source = "db"),
                ServerSetting("llm.outputLanguage", "language", null, default = "auto"),
                ServerSetting("audio.retentionDays", "int", null, default = "14"),
            ),
        )

    private fun viewModel() = ServerSettingsViewModel(api)

    @Test
    fun `the catalog is grouped by key prefix in the server's order`() {
        val groups = viewModel().state.value.groups

        assertEquals(listOf("stt", "llm", "audio"), groups.map { it.first })
        assertEquals(listOf("llm.model", "llm.outputLanguage"), groups[1].second.map { it.setting.key })
    }

    @Test
    fun `an unset value shows its default`() {
        val fields = viewModel().state.value.fields

        assertEquals("auto", fields.first { it.setting.key == "llm.outputLanguage" }.text)
        assertEquals("14", fields.first { it.setting.key == "audio.retentionDays" }.text)
    }

    @Test
    fun `save sends only the changed keys`() {
        val viewModel = viewModel()

        viewModel.edit("llm.model", "gpt-y")
        viewModel.save()

        assertEquals(listOf(mapOf("llm.model" to "gpt-y")), api.sent)
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.anyChanged)
    }

    @Test
    fun `nothing is sent when nothing changed`() {
        val viewModel = viewModel()

        viewModel.save()

        assertTrue(api.sent.isEmpty())
    }

    @Test
    fun `an emptied field asks for the default back`() {
        val viewModel = viewModel()

        viewModel.edit("llm.model", "  ")
        viewModel.save()

        assertEquals(mapOf<String, String?>("llm.model" to null), api.sent.single())
    }

    @Test
    fun `a locked key and an api key cannot be edited`() {
        val viewModel = viewModel()

        viewModel.edit("stt.url", "https://other.example")
        viewModel.edit("stt.apiKey", "sk-nope")

        assertFalse(viewModel.state.value.anyChanged)
    }

    @Test
    fun `a bad value shows its messages under its field`() {
        api.updateAnswer =
            ApiResult.Failure(
                FailureKind.Invalid,
                "The server rejected the request.",
                mapOf("audio.retentionDays" to listOf("Must be 0 to 3650.")),
            )
        val viewModel = viewModel()

        viewModel.edit("audio.retentionDays", "99999")
        viewModel.save()

        val field =
            viewModel.state.value.fields
                .first { it.setting.key == "audio.retentionDays" }
        assertEquals(listOf("Must be 0 to 3650."), field.errors)
        assertEquals("99999", field.text)
        assertEquals("Some values are not valid.", viewModel.state.value.error)
    }

    @Test
    fun `editing a field clears its messages`() {
        api.updateAnswer =
            ApiResult.Failure(FailureKind.Invalid, "x", mapOf("llm.model" to listOf("Too long.")))
        val viewModel = viewModel()
        viewModel.edit("llm.model", "a")
        viewModel.save()

        viewModel.edit("llm.model", "b")

        assertTrue(
            viewModel.state.value.fields
                .first { it.setting.key == "llm.model" }
                .errors
                .isEmpty(),
        )
    }

    @Test
    fun `a v0_1 server says it needs an update`() {
        api.loadAnswer = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = viewModel().state.value

        assertEquals("This server needs an update", state.error)
        assertTrue(state.fields.isEmpty())
        assertFalse(state.loading)
    }

    @Test
    fun `a read token is told to use an admin token`() {
        api.loadAnswer = ApiResult.Failure(FailureKind.Forbidden, "The token is not allowed to do this.")

        assertEquals("The app needs an admin token.", viewModel().state.value.error)
    }

    @Test
    fun `a conflict says a setting is locked`() {
        api.updateAnswer = ApiResult.Failure(FailureKind.Conflict, "conflict")
        val viewModel = viewModel()
        viewModel.edit("llm.model", "z")

        viewModel.save()

        assertTrue(
            viewModel.state.value.error!!
                .contains("locked"),
        )
        assertNull(
            viewModel.state.value.fields
                .first()
                .errors
                .firstOrNull(),
        )
    }
}
