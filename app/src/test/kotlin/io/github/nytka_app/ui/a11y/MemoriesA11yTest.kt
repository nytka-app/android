package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.Memory
import io.github.nytka_app.core.api.MemoryPage
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.memories.MemoriesTab
import io.github.nytka_app.ui.memories.MemoriesViewModel
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
class MemoriesA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private class FakeMemories(
        private val page: ApiResult<MemoryPage>,
    ) : MemoriesClient {
        override suspend fun memories(
            before: String?,
            limit: Int,
        ) = page

        override suspend fun addMemory(text: String): ApiResult<Memory> = error("unused")

        override suspend fun editMemory(
            id: String,
            text: String,
        ): ApiResult<Memory> = error("unused")

        override suspend fun deleteMemory(id: String): ApiResult<Unit> = error("unused")
    }

    private fun memory(
        id: String,
        text: String,
        created: String,
        title: String? = null,
        conversationId: String? = null,
    ) = Memory(
        id = id,
        text = text,
        source = if (conversationId == null) "user" else "ai",
        conversationId = conversationId,
        conversationTitle = title,
        conversationStartedAt = if (title == null) null else created,
        createdAt = created,
        updatedAt = created,
    )

    private fun rows() =
        MemoryPage(
            listOf(
                memory("a", "Anna lives in Kyiv and works from home", "2026-09-29T09:00:00Z", "Lunch with Anna", "c1"),
                memory("b", "You have a sister", "2026-09-29T08:00:00Z"),
                memory("c", "Prefers tea over coffee", "2026-09-26T08:00:00Z", "A walk", "c2"),
                memory("d", "Moved flats in the spring", "2026-05-01T08:00:00Z"),
            ),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        page: ApiResult<MemoryPage> = ApiResult.Ok(rows()),
    ) {
        val model = MemoriesViewModel(FakeMemories(page), clock)
        compose.setScreen(fontScale, dark) {
            MemoriesTab(onOpenConversation = {}, viewModel = model, people = listOf(Person("p1", "Anna")))
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        page: ApiResult<MemoryPage> = ApiResult.Ok(rows()),
    ) {
        show(fontScale, dark, page)
        compose.assertClean()
    }

    @Test
    fun `memories at 1_0 in light`() = clean(1f)

    @Test
    fun `memories at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `memories at 1_3`() = clean(1.3f)

    @Test
    fun `memories at 2_0`() = clean(2f)

    @Test
    fun `memories at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `no memories at 2_0`() = clean(2f, page = ApiResult.Ok(MemoryPage(emptyList())))

    @Test
    fun `a failed load at 1_0`() = clean(1f, page = ApiResult.Failure(FailureKind.Network, "offline"))

    @Test
    fun `a failed load at 2_0`() = clean(2f, page = ApiResult.Failure(FailureKind.Network, "offline"))

    @Test
    fun `memories read in this order`() {
        show(1f, false)
        assertEquals(
            listOf(
                "Today [heading]",
                "Anna lives in Kyiv and works from home",
                "Lunch with Anna · Today [Button]",
                "Edit this memory: Anna lives in Kyiv and works from home [Button]",
                "Forget this memory: Anna lives in Kyiv and works from home [Button]",
                "You have a sister",
                "Edit this memory: You have a sister [Button]",
                "Forget this memory: You have a sister [Button]",
                "This week [heading]",
                "Prefers tea over coffee",
                "A walk · Saturday, 26 September [Button]",
                "Edit this memory: Prefers tea over coffee [Button]",
                "Forget this memory: Prefers tea over coffee [Button]",
                "Earlier [heading]",
                "Moved flats in the spring",
                "Edit this memory: Moved flats in the spring [Button]",
                "Forget this memory: Moved flats in the spring [Button]",
                "Add a memory [Button]",
            ),
            compose.spokenOrder(),
        )
    }
}
