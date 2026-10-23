package io.github.nytka_app.ui

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LayoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private fun answering(vararg features: String) =
        InfoClient { ApiResult.Ok(ServerInfo("0.34.0", 1, features = features.toList())) }

    @Test
    fun `three tabs once the server lists today, five without it or without an answer`() =
        runTest(dispatcher) {
            val three = LayoutViewModel(answering(ServerInfo.FEATURE_TODAY))
            val five = LayoutViewModel(answering(ServerInfo.FEATURE_PEOPLE))
            val failed = LayoutViewModel { ApiResult.Failure(FailureKind.Network, "No network") }
            assertNull(three.threeTabs.value)

            runCurrent()

            assertEquals(true, three.threeTabs.value)
            assertEquals(false, five.threeTabs.value)
            assertEquals(false, failed.threeTabs.value)
        }

    @Test
    fun `a slow server gets the five tabs first and the three when it answers`() =
        runTest(dispatcher) {
            val answer = CompletableDeferred<ApiResult<ServerInfo>>()
            val model = LayoutViewModel { answer.await() }

            advanceTimeBy(LayoutViewModel.WAIT_MS + 1)
            assertEquals(false, model.threeTabs.value)

            answer.complete(ApiResult.Ok(ServerInfo("0.34.0", 1, features = listOf(ServerInfo.FEATURE_TODAY))))
            runCurrent()
            assertEquals(true, model.threeTabs.value)
        }
}
