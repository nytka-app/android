package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.AcceptedByName
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioRequest
import io.github.nytka_app.core.api.CardAnswer
import io.github.nytka_app.core.api.CardClip
import io.github.nytka_app.core.api.CardsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ForgetResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NameSuggestion
import io.github.nytka_app.core.api.PeopleClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ReviewClient
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.core.api.ReviewProposal
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.core.api.VoiceCard
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.people.PeopleScreen
import io.github.nytka_app.ui.people.PeopleViewModel
import io.github.nytka_app.ui.people.cards.CardsViewModel
import io.github.nytka_app.ui.people.cards.FakeClipPlayer
import io.github.nytka_app.ui.people.review.ReviewViewModel
import io.github.nytka_app.ui.tags.FakeTags
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
class PeopleA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private class FakePeople(
        private val people: ApiResult<List<Person>>,
        private val voices: List<UnnamedVoice>,
    ) : PeopleClient {
        override suspend fun people() = people

        override suspend fun voices() = ApiResult.Ok(voices)

        override suspend fun renamePerson(
            id: String,
            name: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun nameVoice(
            speakerId: String,
            name: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun mergePerson(
            id: String,
            intoId: String,
        ): ApiResult<Unit> = error("unused")

        override suspend fun deletePerson(id: String): ApiResult<Unit> = error("unused")

        override suspend fun forgetPerson(id: String): ApiResult<Boolean> = error("unused")

        override suspend fun forgetEverything(id: String): ApiResult<ForgetResult> = error("unused")
    }

    private class FakeCards(
        private val cards: List<VoiceCard>,
    ) : CardsClient {
        override suspend fun cards() = ApiResult.Ok(cards)

        override suspend fun answer(
            card: VoiceCard,
            answer: CardAnswer,
        ): ApiResult<Unit> = error("unused")

        override suspend fun clipRequest(
            kind: String,
            id: String,
        ): AudioRequest? = null
    }

    private class FakeReview(
        private val items: List<ReviewItem>,
    ) : ReviewClient {
        override suspend fun review(limit: Int) = ApiResult.Ok(items)

        override suspend fun answer(
            kind: String,
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = error("unused")

        override suspend fun suggestions(): ApiResult<List<NameSuggestion>> = ApiResult.Ok(emptyList())

        override suspend fun answerSuggestion(
            id: String,
            accept: Boolean,
        ): ApiResult<Unit> = error("unused")

        override suspend fun acceptAllByName(name: String): ApiResult<AcceptedByName> = error("unused")

        override suspend fun autoApplied(limit: Int): ApiResult<List<NameSuggestion>> = ApiResult.Ok(emptyList())

        override suspend fun undo(id: String): ApiResult<Unit> = error("unused")
    }

    private fun people() =
        listOf(
            Person(
                "p1",
                "Olena Kovalenko",
                voices = listOf("v1"),
                segments = 42,
                lastSeenAt = "2026-09-28T10:00:00Z",
                factCount = 3,
                tags = listOf("work", "family"),
            ),
            Person("p2", "Mykola", segments = 7, lastSeenAt = "2026-09-20T10:00:00Z", factCount = 0),
            Person("p3", "Repairman", segments = 2, named = false),
        )

    private fun voices() =
        listOf(
            UnnamedVoice("s1", "SPEAKER_01", segments = 12, lastSeenAt = "2026-09-28T09:00:00Z"),
            UnnamedVoice("s2", null, segments = 3, lastSeenAt = "2026-09-27T09:00:00Z"),
        )

    private fun cards() =
        listOf(
            VoiceCard("group", "g1", "c1", "Lunch at the office", clip = CardClip("a", "b")),
            VoiceCard("match", "m1", "c2", "Walk", personId = "p1", personName = "Olena Kovalenko", similarity = 0.82),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        list: ApiResult<List<Person>>,
        withCards: Boolean,
    ) {
        val info =
            InfoClient {
                ApiResult.Ok(
                    ServerInfo(
                        "0.36.7",
                        1,
                        ServerInfo.SCOPE_ADMIN,
                        listOf(
                            ServerInfo.FEATURE_PEOPLE,
                            ServerInfo.FEATURE_TAGS,
                            ServerInfo.FEATURE_VOICE_GROUPS,
                            ServerInfo.FEATURE_REVIEW,
                        ),
                    ),
                )
            }
        val model = PeopleViewModel(FakePeople(list, voices()), info, FakeTags(), SavedStateHandle())
        val cardsModel = CardsViewModel(FakeCards(if (withCards) cards() else emptyList()), info, FakeClipPlayer())
        val inbox =
            listOf(
                ReviewItem(
                    "name",
                    "g1",
                    "c1",
                    "Lunch",
                    "2026-10-05T10:00:00Z",
                    "I am Olena",
                    ReviewProposal(name = "Olena"),
                ),
            )
        val reviewModel =
            ReviewViewModel(
                FakeReview(inbox),
                info,
                FakeTags(),
                FakeClipPlayer(),
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC),
            )
        compose.setScreen(fontScale, dark) {
            PeopleScreen(
                onOpenPerson = {},
                onOpenSettings = {},
                viewModel = model,
                cardsViewModel = cardsModel,
                reviewViewModel = reviewModel,
            )
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        list: ApiResult<List<Person>> = ApiResult.Ok(people()),
        withCards: Boolean = true,
    ) {
        show(fontScale, dark, list, withCards)
        compose.assertClean()
    }

    @Test
    fun `people at 1_0 in light`() = clean(1f)

    @Test
    fun `people at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `people at 1_3 in light`() = clean(1.3f)

    @Test
    fun `people at 2_0 in light`() = clean(2f)

    @Test
    fun `people at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `people without cards at 2_0`() = clean(2f, withCards = false)

    @Test
    fun `no people at 2_0`() = clean(2f, list = ApiResult.Ok(emptyList()), withCards = false)

    @Test
    fun `a failed list at 2_0`() =
        clean(2f, list = ApiResult.Failure(FailureKind.Network, "The server did not answer."), withCards = false)

    @Test
    fun `a monogram letter fits its disc at 2_0 and is not spoken`() {
        compose.setScreen(2f, false) {
            Monogram("Olena", size = 24.dp)
            Monogram("Olena", size = 40.dp, unknown = true)
        }
        assertEquals(emptyList<String>(), compose.clipped(unmerged = true))
        assertEquals(emptyList<String>(), compose.spokenOrder())
    }

    @Test
    fun `people read in this order`() {
        show(1f, false, ApiResult.Ok(people()), withCards = true)
        assertEquals(
            listOf(
                "Who is this? [heading]",
                "Heard in Lunch at the office",
                "Play clip [Button]",
                "Name",
                "Olena Kovalenko [Button]",
                "Mykola [Button]",
                "Save [Button]",
                "Not a person [Button]",
                "Skip [Button]",
                "Is this Olena Kovalenko? [heading]",
                "Heard in Walk",
                "Yes [Button]",
                "Not them [Button]",
                "Skip [Button]",
                "People [heading]",
                "Olena Kovalenko, Heard 28 Sep · 3 facts, work, family, Needs you",
                "Mykola, Heard 20 Sep · 0 facts",
                "Repairman, Name not known yet, Not heard yet",
                "Unnamed voices [heading]",
                "SPEAKER_01, 12 lines · last heard 28 Sep",
                "Unknown voice, 3 lines · last heard 27 Sep",
                "People",
                "Tag [Button]",
                "Review inbox, 1 [Button]",
                "More [Button]",
            ),
            compose.spokenOrder(),
        )
    }
}
