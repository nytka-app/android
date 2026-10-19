package io.github.nytka_app.core.location

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.ConversationSummary
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.Segment
import io.github.nytka_app.core.api.Tag
import io.github.nytka_app.core.api.TagSuggestion
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.settings.SettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationTagSyncTest {
    private class FakeSettings(
        initial: Settings,
    ) : SettingsSource {
        val state = MutableStateFlow(initial)
        override val settings: Flow<Settings> = state

        override suspend fun current() = state.value

        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private class FakeConversations(
        var answer: ApiResult<ConversationPage> = ApiResult.Ok(ConversationPage(emptyList())),
    ) : ConversationsClient {
        var calls = 0

        override suspend fun conversations(
            before: String?,
            limit: Int,
        ): ApiResult<ConversationPage> {
            calls++
            return answer
        }

        override suspend fun conversation(id: String): ApiResult<ConversationDetail> = error("not used")

        override suspend fun renameConversation(
            id: String,
            title: String?,
        ): ApiResult<ConversationDetail> = error("not used")

        override suspend fun nameVoice(
            speakerId: String,
            name: String,
        ): ApiResult<Unit> = error("not used")

        override suspend fun markSegment(
            segmentId: Long,
            isUser: Boolean?,
        ): ApiResult<Segment> = error("not used")

        override suspend fun enrichConversation(id: String): ApiResult<Unit> = error("not used")

        override suspend fun deleteConversation(id: String): ApiResult<Unit> = error("not used")

        override suspend fun transcriptionsJson(id: String): ApiResult<String> = error("not used")
    }

    private class FakeTags(
        var answer: ApiResult<List<String>> = ApiResult.Ok(listOf("Home")),
    ) : TagsClient {
        val added = mutableListOf<Pair<String, String>>()

        override suspend fun tags(q: String?): ApiResult<List<Tag>> = error("not used")

        override suspend fun addConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> {
            added += id to name
            return answer
        }

        override suspend fun removeConversationTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("not used")

        override suspend fun addPersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("not used")

        override suspend fun removePersonTag(
            id: String,
            name: String,
        ): ApiResult<List<String>> = error("not used")

        override suspend fun conversations(
            tag: String,
            before: String?,
            limit: Int,
        ): ApiResult<ConversationPage> = error("not used")

        override suspend fun people(tag: String): ApiResult<List<Person>> = error("not used")

        override suspend fun suggestions(): ApiResult<List<TagSuggestion>> = error("not used")

        override suspend fun answerSuggestion(
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = error("not used")
    }

    private fun summary(
        id: String,
        startedAt: String,
    ) = ConversationSummary(id, startedAt, endedAt = startedAt, status = "done", preview = "")

    private val now = 1_760_000_000_000L // 2025-10-09T09:46:40Z

    @Test
    fun `off, it never calls the conversations or tags API`() =
        runTest {
            val settings =
                FakeSettings(Settings(locationTags = false, pendingLocationPlace = "Home", pendingLocationAt = now))
            val conversations = FakeConversations()
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.OFF, result)
            assertEquals(0, conversations.calls)
            assertEquals(0, tags.added.size)
        }

    @Test
    fun `on with nothing pending, it never calls the conversations or tags API`() =
        runTest {
            val settings = FakeSettings(Settings(locationTags = true, pendingLocationPlace = null))
            val conversations = FakeConversations()
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.EMPTY, result)
            assertEquals(0, conversations.calls)
            assertEquals(0, tags.added.size)
        }

    @Test
    fun `a pending place matches the conversation that started near it and is sent as its tag`() =
        runTest {
            val settings =
                FakeSettings(Settings(locationTags = true, pendingLocationPlace = "Home", pendingLocationAt = now))
            val startedAt =
                java.time.Instant
                    .ofEpochMilli(now + 60_000)
                    .toString()
            val conversations = FakeConversations(ApiResult.Ok(ConversationPage(listOf(summary("c1", startedAt)))))
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.TAGGED, result)
            assertEquals(listOf("c1" to "Home"), tags.added)
            assertNull(settings.current().pendingLocationPlace)
        }

    @Test
    fun `no conversation starts near the pending place, so nothing is tagged and the place stays pending`() =
        runTest {
            val settings =
                FakeSettings(Settings(locationTags = true, pendingLocationPlace = "Home", pendingLocationAt = now))
            val farAway =
                java.time.Instant
                    .ofEpochMilli(now - 60 * 60_000)
                    .toString()
            val conversations = FakeConversations(ApiResult.Ok(ConversationPage(listOf(summary("c1", farAway)))))
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.NO_MATCH, result)
            assertEquals(0, tags.added.size)
            assertEquals("Home", settings.current().pendingLocationPlace)
        }

    @Test
    fun `a pending place older than a day is dropped instead of tagging whatever is newest`() =
        runTest {
            val oldAt = now - 25 * 60 * 60_000
            val settings =
                FakeSettings(Settings(locationTags = true, pendingLocationPlace = "Home", pendingLocationAt = oldAt))
            val conversations = FakeConversations()
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.EXPIRED, result)
            assertEquals(0, conversations.calls)
            assertNull(settings.current().pendingLocationPlace)
        }

    @Test
    fun `a conversations API failure is reported and the place stays pending for the next try`() =
        runTest {
            val settings =
                FakeSettings(Settings(locationTags = true, pendingLocationPlace = "Home", pendingLocationAt = now))
            val conversations = FakeConversations(ApiResult.Failure(FailureKind.Network, "offline"))
            val tags = FakeTags()

            val result = LocationTagSync(settings, conversations, tags) { now }.sync()

            assertEquals(LocationTagResult.FAILED, result)
            assertEquals("Home", settings.current().pendingLocationPlace)
        }
}
