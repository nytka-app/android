package io.github.nytka_app.ui.overtime

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Insight
import io.github.nytka_app.core.api.InsightProgress
import io.github.nytka_app.core.api.Insights
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.NEEDS_UPDATE
import io.github.nytka_app.ui.today.FakeInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OverTimeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun info(vararg features: String) =
        InfoClient {
            ApiResult.Ok(ServerInfo("0.34.0", 1, features = features.toList()))
        }

    @Test
    fun `a server without insights needs an update and is not asked`() {
        val api = FakeInsights()

        val state = OverTimeViewModel(api, info(ServerInfo.FEATURE_TODAY)).state.value

        assertEquals(NEEDS_UPDATE, state.error)
        assertEquals(0, api.calls)
    }

    @Test
    fun `unlocked first, then the locked nearest to unlocking`() {
        val api =
            FakeInsights(
                ApiResult.Ok(
                    Insights(
                        daysWithAudio = 9,
                        trustedAfterDays = 14,
                        insights =
                            listOf(
                                Insight("far", "Far", progress = InsightProgress(2, 30)),
                                Insight("open", "Open", unlocked = true, summary = "Mostly mornings."),
                                Insight("near", "Near", progress = InsightProgress(9, 14)),
                            ),
                    ),
                ),
            )

        val state = OverTimeViewModel(api, info(ServerInfo.FEATURE_INSIGHTS)).state.value

        assertNull(state.error)
        assertEquals(listOf("open", "near", "far"), state.ordered.map { it.id })
    }
}
