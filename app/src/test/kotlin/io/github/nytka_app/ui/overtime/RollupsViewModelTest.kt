package io.github.nytka_app.ui.overtime

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Rollup
import io.github.nytka_app.core.api.RollupsClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.NEEDS_UPDATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RollupsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val weeks =
        listOf(
            Rollup(Rollup.WEEK, "2026-10-19", "2026-10-25", summary = "This week"),
            Rollup(Rollup.WEEK, "2026-10-12", "2026-10-18", summary = "Last week"),
        )
    private val asked = mutableListOf<String>()
    private val api =
        object : RollupsClient {
            override suspend fun rollups(
                period: String,
                before: String?,
                limit: Int,
            ): ApiResult<List<Rollup>> {
                asked += "$period $before"
                val list =
                    if (period ==
                        Rollup.WEEK
                    ) {
                        weeks
                    } else {
                        listOf(Rollup(period, "2026-01-01", "2026-12-31", locked = true))
                    }
                return ApiResult.Ok(list.filter { before == null || it.start < before }.take(limit))
            }
        }

    private fun info(vararg features: String) =
        InfoClient {
            ApiResult.Ok(ServerInfo("0.35.0", 1, features = features.toList()))
        }

    @Test
    fun `a server without rollups shows no section`() {
        val model = RollupsViewModel(api, info(ServerInfo.FEATURE_INSIGHTS))

        assertFalse(model.state.value.available)
        assertTrue(model.state.value.checked)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a server with summaries but no insights yet is not told it needs an update`() {
        val needs = NEEDS_UPDATE

        assertNull(shownError(needs, RollupsUiState(available = true, checked = true)))
        assertNull(shownError(needs, RollupsUiState(checked = false)))
        assertEquals(needs, shownError(needs, RollupsUiState(available = false, checked = true)))
        assertEquals("Offline", shownError("Offline", RollupsUiState(available = true, checked = true)))
    }

    @Test
    fun `opens on this week and steps back and forth`() {
        val model = RollupsViewModel(api, info(ServerInfo.FEATURE_ROLLUPS))
        assertEquals(
            "This week",
            model.state.value.rollup
                ?.summary,
        )

        model.earlier()
        assertEquals(
            "Last week",
            model.state.value.rollup
                ?.summary,
        )
        assertTrue(model.state.value.canGoLater)

        model.later()
        assertEquals(
            "This week",
            model.state.value.rollup
                ?.summary,
        )
        assertFalse(model.state.value.canGoLater)
        assertEquals(listOf("week null", "week 2026-10-19"), asked)
    }

    @Test
    fun `past the oldest summary it stays and stops going back`() {
        val model = RollupsViewModel(api, info(ServerInfo.FEATURE_ROLLUPS))

        model.earlier()
        model.earlier()

        assertEquals(
            "Last week",
            model.state.value.rollup
                ?.summary,
        )
        assertFalse(model.state.value.canGoEarlier)
    }

    @Test
    fun `another period starts over on its newest summary`() {
        val model = RollupsViewModel(api, info(ServerInfo.FEATURE_ROLLUPS))
        model.earlier()

        model.show(Rollup.YEAR)

        assertTrue(
            model.state.value.rollup
                ?.locked == true,
        )
        assertFalse(model.state.value.canGoLater)
    }

    @Test
    fun `dates read as a range`() {
        assertEquals("19 Oct – 25 Oct 2026", rangeOf(weeks[0]))
        assertEquals("29 Dec 2025 – 4 Jan 2026", rangeOf(Rollup(Rollup.WEEK, "2025-12-29", "2026-01-04")))
    }
}
