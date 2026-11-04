package it.danielebufarini.spesify.ui.screens.dashboard

import it.danielebufarini.spesify.data.DashboardBalanceTrend
import it.danielebufarini.spesify.data.DashboardMonthSummary
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.data.RecurringExpenseOverview
import it.danielebufarini.spesify.data.WidgetMonthSummary
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DashboardStateStoreTest {
    @Test
    fun navigationCrossesYearsAndKeepsThePublicMonthReadOnly() = runTest {
        val store = DashboardStateStore(EmptyDashboardRepository, MonthCursor(2026, 1), backgroundScope)
        assertFalse(store.selectedMonth is MutableStateFlow<*>)
        store.selectPreviousMonth()
        assertEquals(MonthCursor(2025, 12), store.selectedMonth.value)
        store.selectNextMonth()
        assertEquals(MonthCursor(2026, 1), store.selectedMonth.value)
    }

    @Test
    fun concurrentNavigationDoesNotLoseAcceptedMonthChanges() = runTest {
        val initial = MonthCursor(2026, 11)
        val store = DashboardStateStore(EmptyDashboardRepository, initial, backgroundScope)
        val workers = 8
        val steps = 1_000
        runTogether(workers) { repeat(steps) { store.selectNextMonth() } }
        val absoluteMonth = initial.year * 12 + initial.month - 1 + workers * steps
        assertEquals(MonthCursor(absoluteMonth / 12, absoluteMonth % 12 + 1), store.selectedMonth.value)
        runTogether(workers) { repeat(steps) { store.selectPreviousMonth() } }
        assertEquals(initial, store.selectedMonth.value)
    }

    private suspend fun runTogether(workers: Int, action: () -> Unit) = coroutineScope {
        val ready = Channel<Unit>(workers)
        val start = CompletableDeferred<Unit>()
        val jobs = List(workers) {
            launch(Dispatchers.Default) {
                ready.send(Unit)
                start.await()
                action()
            }
        }
        repeat(workers) { ready.receive() }
        start.complete(Unit)
        jobs.joinAll()
        ready.close()
    }
}

private object EmptyDashboardRepository : DashboardReadRepository {
    override fun getDashboardMonthSummary(year: Int, month: Int): Flow<DashboardMonthSummary> =
        flowOf(emptyDashboardMonthSummary())
    override fun getDashboardBalanceTrend(selectedYear: Int, selectedMonth: Int, trailingMonthCount: Int) =
        flowOf(DashboardBalanceTrend(expenseTotalsByMonth = emptyList(), incomeTotalsByMonth = emptyList()))
    override fun getDashboardRecentTransactions(limit: Int): Flow<List<DashboardRecentTransaction>> = flowOf(emptyList())
    override fun getRecurringExpenseOverviewForMonth(year: Int, month: Int) =
        flowOf(RecurringExpenseOverview(expenses = emptyList(), totalAmount = 0L, nextOccurrenceDate = null))
    override suspend fun getWidgetMonthSummary(year: Int, month: Int) = WidgetMonthSummary(0L, 0L)
}
