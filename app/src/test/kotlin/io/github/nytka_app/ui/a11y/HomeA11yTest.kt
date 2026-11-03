package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.TodayPage
import io.github.nytka_app.ui.today.FakeItemMarks
import io.github.nytka_app.ui.today.FakeToday
import io.github.nytka_app.ui.today.TodayLinks
import io.github.nytka_app.ui.today.TodayScreen
import io.github.nytka_app.ui.today.TodayViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class HomeA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-23T17:00:00Z"), ZoneId.of("Europe/Kyiv"))

    private fun page() =
        TodayPage(
            "2026-10-23",
            listOf(
                LifeItem(
                    "task",
                    "t1",
                    "Send Olena the contract",
                    at = "2026-10-23T06:00:00Z",
                    personId = "p1",
                    personName = "Olena",
                    due = "2026-10-24",
                ),
                LifeItem(
                    "memory",
                    "m1",
                    "Mykola moves flats in November",
                    at = "2026-10-23T10:00:00Z",
                    salience = "vital",
                ),
                LifeItem("task", "t2", "Book the dentist", at = "2026-10-23T16:00:00Z"),
            ),
        )

    private fun show(
        fontScale: Float = 1f,
        dark: Boolean = false,
        today: ApiResult<TodayPage> = ApiResult.Ok(page()),
    ) {
        val api = FakeToday().also { it.today = today }
        val model = TodayViewModel(api, FakeItemMarks(), clock)
        compose.setScreen(fontScale, dark) {
            TodayScreen(
                onOpenMoment = { _, _ -> },
                onOpenPerson = {},
                links = TodayLinks({}, {}, {}, {}),
                viewModel = model,
            )
        }
    }

    @Test
    fun `home at 1_0 in light`() = clean(1f)

    @Test
    fun `home at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `home at 1_3 in light`() = clean(1.3f)

    @Test
    fun `home at 2_0 in light`() = clean(2f)

    @Test
    fun `home at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `home with no items at 2_0`() = clean(2f, today = ApiResult.Ok(TodayPage("2026-10-23", emptyList())))

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        today: ApiResult<TodayPage> = ApiResult.Ok(page()),
    ) {
        show(fontScale, dark, today)
        compose.assertClean()
    }
}
