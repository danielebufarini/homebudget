package it.danielebufarini.homebudget.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshCoordinatorTest {

    @Test
    fun requestRefresh_coalescesBurstRequests() = runTest {
        var refreshCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = WidgetRefreshCoordinator(
            coroutineContext = Job() + dispatcher,
            debounceMillis = 250L,
            refreshAction = { refreshCount += 1 }
        )

        coordinator.requestRefresh()
        coordinator.requestRefresh()
        coordinator.requestRefresh()
        runCurrent()

        advanceTimeBy(249L)
        runCurrent()
        assertEquals(0, refreshCount)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, refreshCount)
    }

    @Test
    fun refreshNow_cancelsPendingRefreshAndRunsImmediately() = runTest {
        var refreshCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = WidgetRefreshCoordinator(
            coroutineContext = Job() + dispatcher,
            debounceMillis = 250L,
            refreshAction = { refreshCount += 1 }
        )

        coordinator.requestRefresh()
        runCurrent()

        advanceTimeBy(100L)
        coordinator.refreshNow()
        runCurrent()
        assertEquals(1, refreshCount)

        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, refreshCount)
    }
}
