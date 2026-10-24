package io.github.nytka_app.ui.topics

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Topic
import io.github.nytka_app.core.api.TopicMoment
import io.github.nytka_app.core.api.TopicPage
import io.github.nytka_app.core.api.TopicsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TopicsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val pages = mutableMapOf<String?, ApiResult<TopicPage>>()
    private val asked = mutableListOf<String?>()
    private val api =
        object : TopicsClient {
            override suspend fun topics(kind: String?): ApiResult<List<Topic>> =
                ApiResult.Ok(
                    listOf(
                        Topic("t1", Topic.KIND_TAG, "lease", lastAt = "2026-10-20T09:00:00Z"),
                        Topic("t2", Topic.KIND_PERSON, "Ann", lastAt = "2026-10-23T09:00:00Z"),
                        Topic("t3", Topic.KIND_TAG, "launch", lastAt = "2026-10-22T09:00:00Z"),
                    ),
                )

            override suspend fun topic(
                id: String,
                before: String?,
            ): ApiResult<TopicPage> {
                asked += before
                return pages.getValue(before)
            }
        }
    private val clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC)

    private fun moment(at: String) = TopicMoment("c1", at)

    @Test
    fun `newest heard first, filtered by kind and by name`() {
        val model = TopicsViewModel(api)
        assertEquals(
            listOf("Ann", "launch", "lease"),
            model.state.value.shown
                .map { it.name },
        )

        model.setKind(Topic.KIND_TAG)
        assertEquals(
            listOf("launch", "lease"),
            model.state.value.shown
                .map { it.name },
        )

        model.setQuery("LEA")
        assertEquals(
            listOf("lease"),
            model.state.value.shown
                .map { it.name },
        )
    }

    @Test
    fun `a topic pages its moments until the server has no more`() {
        pages[null] =
            ApiResult.Ok(
                TopicPage("t1", "tag", "lease", timeline = listOf(moment("2026-10-23T09:00:00Z")), nextBefore = "p2"),
            )
        pages["p2"] = ApiResult.Ok(TopicPage("t1", "tag", "lease", timeline = listOf(moment("2026-10-20T09:00:00Z"))))
        val model = TopicViewModel(SavedStateHandle(mapOf("id" to "t1")), api, clock)

        model.loadMore()
        model.loadMore()

        assertEquals(listOf(null, "p2"), asked)
        assertEquals(
            2,
            model.state.value.page
                ?.timeline
                ?.size,
        )
        assertNull(
            model.state.value.page
                ?.nextBefore,
        )
    }
}
