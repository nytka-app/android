package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.AiState
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerStatus
import io.github.nytka_app.core.api.StatusClient
import io.github.nytka_app.core.api.Tag
import io.github.nytka_app.core.api.TagSuggestion
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.conversations.ConversationsScreen
import io.github.nytka_app.ui.conversations.ConversationsViewModel
import io.github.nytka_app.ui.conversations.FakeConversations
import io.github.nytka_app.ui.conversations.FakeSpeech
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class ConversationsA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private class FakeTags(
        val filtered: ConversationPage,
    ) : TagsClient {
        override suspend fun conversations(
            tag: String,
            before: String?,
            limit: Int,
        ) = ApiResult.Ok(filtered)

        override suspend fun tags(q: String?) = ApiResult.Ok(listOf(Tag("work", 3, 1, 4)))

        override suspend fun addConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun removeConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun addPersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun removePersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("unused")

        override suspend fun people(tag: String): ApiResult<List<Person>> = error("unused")

        override suspend fun suggestions(): ApiResult<List<TagSuggestion>> = error("unused")

        override suspend fun answerSuggestion(
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = error("unused")
    }

    private fun rows() =
        listOf(
            ConversationSummary(
                "c1",
                "2026-09-29T10:00:00Z",
                "2026-09-29T10:40:00Z",
                "closed",
                "We talked about the flat and the contract",
                title = "Flat and the contract with Olena",
                summary = "Two flats on Saturday, the contract before Friday.",
                aiStatus = AiState.NONE,
                tags = listOf("work", "family"),
            ),
            ConversationSummary(
                "c2",
                "2026-09-29T08:00:00Z",
                "2026-09-29T08:10:00Z",
                "closed",
                "A podcast about trains",
                mediaShare = 0.9,
            ),
            ConversationSummary(
                "c3",
                "2026-09-28T09:00:00Z",
                "2026-09-28T09:05:00Z",
                "closed",
                "Short note",
                aiStatus = AiState.FAILED,
            ),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        items: List<ConversationSummary>,
        tag: String? = null,
    ) {
        val api = FakeConversations().also { it.pages[null] = ApiResult.Ok(ConversationPage(items)) }
        val model =
            ConversationsViewModel(
                api,
                StatusClient { ApiResult.Ok(ServerStatus(pendingChunks = 0)) },
                clock,
                FakeTags(ConversationPage(items)),
                InfoClient { ApiResult.Ok(ServerInfo("0.36.7", 1, features = listOf(ServerInfo.FEATURE_TAGS))) },
                FakeSpeech(),
                SavedStateHandle(),
            )
        tag?.let(model::showTag)
        compose.setScreen(fontScale, dark) {
            ConversationsScreen(
                status = StatusUiState(muted = true, serverLine = "Uploaded 2 min ago"),
                onMute = {},
                deleted = null,
                onOpen = {},
                onSearch = {},
                viewModel = model,
            )
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        items: List<ConversationSummary> = rows(),
        tag: String? = null,
    ) {
        show(fontScale, dark, items, tag)
        compose.assertClean()
    }

    @Test
    fun `conversations at 1_0 in light`() = clean(1f)

    @Test
    fun `conversations at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `conversations at 1_3 in light`() = clean(1.3f)

    @Test
    fun `conversations at 2_0 in light`() = clean(2f)

    @Test
    fun `conversations at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `empty list at 2_0`() = clean(2f, items = emptyList())

    @Test
    fun `tag filter on at 1_0`() = clean(1f, tag = "work")

    @Test
    fun `tag filter on at 2_0`() = clean(2f, tag = "work")

    @Test
    fun `a filtered list reads in this order`() {
        show(1f, false, rows(), "work")
        assertEquals(
            listOf(
                "Tag [Button]",
                "Search [Button]",
                "Tag: work",
                "Clear tag filter work [Button]",
                "Recording off",
                "Pendant battery: Unknown",
                "Server: Uploaded 2 min ago",
                "Queued chunks: 0",
                "Unmute [Button]",
                "Today [heading]",
                "Flat and the contract with Olena, Two flats on Saturday, the contract before Friday., " +
                    "10:00–10:40 · 40 min, work, family",
                "08:00–08:10, A podcast about trains, 08:00–08:10 · 10 min, Media",
                "Yesterday [heading]",
                "09:00–09:05, Short note, 09:00–09:05 · 5 min, Summary failed",
            ),
            compose.spokenOrder(),
        )
    }
}
