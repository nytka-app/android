package io.github.nytka_app.ui.conversations

import androidx.lifecycle.SavedStateHandle
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationDetail
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.FakeTags
import io.github.nytka_app.ui.tags.TagAccess
import io.github.nytka_app.ui.tags.TagNotice
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Chips on the conversation screen: what shows, adding and removing. */
class ConversationTagsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeConversations()
    private val tags = FakeTags()

    init {
        api.detail =
            ApiResult.Ok(
                ConversationDetail(
                    id = "c1",
                    startedAt = "2026-09-29T08:00:00Z",
                    endedAt = "2026-09-29T08:10:00Z",
                    status = "closed",
                    segments = emptyList(),
                    tags = listOf("family", "work"),
                ),
            )
    }

    private fun viewModel(info: InfoClient = fakeInfo(ServerInfo.FEATURE_TAGS)) =
        ConversationViewModel(
            SavedStateHandle(mapOf("id" to "c1")),
            api,
            FakeTasks(emptyList()),
            FakeBookmarks(),
            FakeReview(),
            FakeAudio(),
            FakePlayer(),
            clock,
            info,
            tags,
            FakeSpeech(),
        )

    private fun failure(kind: FailureKind) = ApiResult.Failure(kind, "The server answered.")

    @Test
    fun `the detail's tags show with an admin token`() {
        val state = viewModel().state.value

        assertEquals(listOf("family", "work"), state.tags)
        assertEquals(TagAccess.Edit, state.tagAccess)
    }

    @Test
    fun `a read token shows the chips but changes nothing`() {
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_TAGS, scope = ServerInfo.SCOPE_READ))

        vm.addTag("work")
        vm.removeTag("family")

        assertEquals(TagAccess.Read, vm.state.value.tagAccess)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertEquals(0, tags.calls)
    }

    @Test
    fun `a server without tags shows no chips and makes no call`() {
        val vm = viewModel(fakeInfo(ServerInfo.FEATURE_PEOPLE))

        vm.addTag("work")
        vm.removeTag("work")

        assertEquals(TagAccess.None, vm.state.value.tagAccess)
        assertEquals(0, tags.calls)
    }

    @Test
    fun `an unreadable info shows no chips`() {
        val vm = viewModel(InfoClient { ApiResult.Failure(FailureKind.Network, "No network.") })

        vm.addTag("work")

        assertEquals(TagAccess.None, vm.state.value.tagAccess)
        assertEquals(0, tags.calls)
    }

    @Test
    fun `adding replaces the chips with the server's answer`() {
        tags.answer = ApiResult.Ok(listOf("family", "work", "робота"))
        val vm = viewModel()

        vm.addTag("  Робота ")

        assertEquals(listOf("add conversations c1 Робота"), tags.writes)
        assertEquals(listOf("family", "work", "робота"), vm.state.value.tags)
        assertNull(vm.state.value.tagNotice)
    }

    @Test
    fun `an empty name sends nothing`() {
        val vm = viewModel()

        vm.addTag("   ")

        assertEquals(0, tags.calls)
    }

    @Test
    fun `removing drops the chip at once`() {
        val gate = CompletableDeferred<Unit>()
        tags.gate = gate
        tags.answer = ApiResult.Ok(listOf("work"))
        val vm = viewModel()

        vm.removeTag("family")

        assertEquals(listOf("work"), vm.state.value.tags)
        assertEquals(listOf("remove conversations c1 family"), tags.writes)
        gate.complete(Unit)
        assertEquals(listOf("work"), vm.state.value.tags)
    }

    @Test
    fun `a refused remove brings the chip back with a notice`() {
        tags.answer = failure(FailureKind.Network)
        val vm = viewModel()

        vm.removeTag("family")

        assertEquals(listOf("family", "work"), vm.state.value.tags)
        assertEquals(TagNotice.Failed("The server answered."), vm.state.value.tagNotice)
    }

    @Test
    fun `the notice says what the server refused`() {
        val vm = viewModel()

        tags.answer = failure(FailureKind.Invalid)
        vm.addTag("a/b")
        assertEquals(TagNotice.InvalidTag, vm.state.value.tagNotice)

        tags.answer = failure(FailureKind.Conflict)
        vm.addTag("twenty-one")
        assertEquals(TagNotice.TooManyTags, vm.state.value.tagNotice)

        tags.answer = failure(FailureKind.Forbidden)
        vm.addTag("work")
        assertEquals(TagNotice.NeedsAdmin, vm.state.value.tagNotice)

        tags.answer = failure(FailureKind.NotFound)
        vm.addTag("work")
        assertEquals(TagNotice.Gone, vm.state.value.tagNotice)

        vm.tagNoticeShown()
        assertNull(vm.state.value.tagNotice)
        assertEquals(listOf("family", "work"), vm.state.value.tags)
    }
}
