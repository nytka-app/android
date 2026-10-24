package io.github.nytka_app.ui.today

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.RelatedClient
import io.github.nytka_app.core.api.RelatedMoment
import io.github.nytka_app.core.api.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RelatedViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val moment = RelatedMoment("c0", "2026-10-20T09:00:00Z", title = "Lease call")
    private val asked = mutableListOf<String>()
    private val api =
        object : RelatedClient {
            override suspend fun related(
                kind: String,
                id: String,
                limit: Int,
            ): ApiResult<List<RelatedMoment>> {
                asked += "$kind $id"
                return ApiResult.Ok(listOf(moment))
            }
        }

    private fun info(vararg features: String) =
        InfoClient {
            ApiResult.Ok(ServerInfo("0.35.0", 1, features = features.toList()))
        }

    @Test
    fun `moments the item carries show without asking`() {
        val model = RelatedViewModel(SavedStateHandle(), api, info(ServerInfo.FEATURE_RELATED))

        model.load("task", "t1", known = listOf(moment))

        assertEquals(listOf(moment), model.moments.value)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `otherwise asked once per thing`() {
        val model = RelatedViewModel(SavedStateHandle(), api, info(ServerInfo.FEATURE_RELATED))

        model.load("memory", "m1")
        model.load("memory", "m1")

        assertEquals(listOf("memory m1"), asked)
        assertEquals(listOf(moment), model.moments.value)
    }

    @Test
    fun `a server without related is not asked and shows no strip`() {
        val model = RelatedViewModel(SavedStateHandle(), api, info(ServerInfo.FEATURE_TODAY))

        model.load("task", "t1")

        assertEquals(emptyList<String>(), asked)
        assertEquals(emptyList<RelatedMoment>(), model.moments.value)
    }

    @Test
    fun `on a conversation route the strip knows its conversation`() {
        assertEquals("c9", RelatedViewModel(SavedStateHandle(mapOf("id" to "c9")), api, info()).routeConversation)
        assertNull(RelatedViewModel(SavedStateHandle(), api, info()).routeConversation)
    }
}
