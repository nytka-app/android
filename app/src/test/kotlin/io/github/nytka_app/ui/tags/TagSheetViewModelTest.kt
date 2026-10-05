package io.github.nytka_app.ui.tags

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagSheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val tags =
        FakeTags().apply {
            inUse = listOf(Tag("work", 3, 1, 4), Tag("workout", 0, 2, 2), Tag("family", 1, 1, 2))
        }

    @Test
    fun `opening the sheet lists the tags in use at once`() =
        runTest {
            val vm = TagSheetViewModel(tags)
            runCurrent()

            assertEquals(
                listOf("work", "workout", "family"),
                vm.state.value.tags
                    .map { it.name },
            )
            assertEquals(listOf<String?>(""), tags.queries)
        }

    @Test
    fun `typing lists the tags that start with it, after a pause`() =
        runTest {
            val vm = TagSheetViewModel(tags)
            runCurrent()

            vm.setQuery("w")
            vm.setQuery("wo")
            advanceTimeBy(TagSheetViewModel.DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(listOf<String?>(""), tags.queries)

            advanceTimeBy(1)
            runCurrent()
            assertEquals("wo", vm.state.value.query)
            assertEquals(
                listOf("work", "workout"),
                vm.state.value.tags
                    .map { it.name },
            )
            assertEquals(listOf<String?>("", "wo"), tags.queries)
        }

    @Test
    fun `clearing the field lists them all again without waiting`() =
        runTest {
            val vm = TagSheetViewModel(tags)
            vm.setQuery("fam")
            advanceTimeBy(TagSheetViewModel.DEBOUNCE_MS)
            runCurrent()

            vm.setQuery("")
            runCurrent()

            assertEquals(3, vm.state.value.tags.size)
        }
}
