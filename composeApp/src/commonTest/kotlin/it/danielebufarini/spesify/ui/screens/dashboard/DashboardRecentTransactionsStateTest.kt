package it.danielebufarini.spesify.ui.screens.dashboard

import it.danielebufarini.spesify.data.DashboardBalanceTrend
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.data.RecurringExpenseOverview
import it.danielebufarini.spesify.data.WidgetMonthSummary
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRecentTransactionsStateTest {
    private val zone = TimeZone.of("Pacific/Auckland")

    @Test
    fun toggleUsesTodayInTheDeviceZoneIndependentlyOfSelectedMonth() = runTest {
        val repository = RecordingDashboardRepository()
        val hideFuture = MutableStateFlow(false)
        val store = DashboardStateStore(
            repository, MonthCursor(2020, 1), backgroundScope, hideFuture,
            clockFrom("2026-11-06T23:59:59+13:00"), StandardTestDispatcher(testScheduler)
        ) { zone }
        backgroundScope.launch { store.recentTransactions.collect() }

        assertEquals(RecentQuery(50, null), repository.requests.receive())
        hideFuture.value = true
        assertEquals(RecentQuery(50, dayStart(2026, 11, 7)), repository.requests.receive())
        store.selectNextMonth()
        hideFuture.value = false
        assertEquals(RecentQuery(50, null), repository.requests.receive())
        assertEquals(MonthCursor(2020, 2), store.selectedMonth.value)
        assertTrue(repository.requests.tryReceive().isFailure)
    }

    @Test
    fun midnightRefreshesTheQueryWhileTheHomeRemainsVisible() = runTest {
        val repository = RecordingDashboardRepository()
        val store = DashboardStateStore(
            repository, MonthCursor(2026, 11), backgroundScope, MutableStateFlow(true),
            clockFrom("2026-11-06T23:59:59+13:00"), StandardTestDispatcher(testScheduler)
        ) { zone }
        backgroundScope.launch { store.recentTransactions.collect() }
        backgroundScope.launch { store.refreshRecentTransactionsWhileVisible() }
        assertEquals(RecentQuery(50, dayStart(2026, 11, 7)), repository.requests.receive())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RecentQuery(50, dayStart(2026, 11, 8)), repository.requests.receive())
    }

    @Test
    fun returningToForegroundRefreshesAfterDaysSpentInBackground() = runTest {
        val repository = RecordingDashboardRepository()
        val store = DashboardStateStore(
            repository, MonthCursor(2026, 11), backgroundScope, MutableStateFlow(true),
            clockFrom("2026-11-06T12:00:00+13:00"), StandardTestDispatcher(testScheduler)
        ) { zone }
        backgroundScope.launch { store.recentTransactions.collect() }
        val firstVisit = backgroundScope.launch { store.refreshRecentTransactionsWhileVisible() }
        assertEquals(RecentQuery(50, dayStart(2026, 11, 7)), repository.requests.receive())
        runCurrent()
        firstVisit.cancelAndJoin()
        advanceTimeBy(48.hours)
        runCurrent()
        assertTrue(repository.requests.tryReceive().isFailure)

        backgroundScope.launch { store.refreshRecentTransactionsWhileVisible() }
        assertEquals(RecentQuery(50, dayStart(2026, 11, 9)), repository.requests.receive())
    }

    @Test
    fun daylightSavingUsesCalendarMidnightInsteadOfAddingTwentyFourHours() = runTest {
        val rome = TimeZone.of("Europe/Rome")
        val repository = RecordingDashboardRepository()
        val store = DashboardStateStore(
            repository, MonthCursor(2026, 3), backgroundScope, MutableStateFlow(true),
            clockFrom("2026-03-29T00:00:00+01:00"), StandardTestDispatcher(testScheduler)
        ) { rome }
        backgroundScope.launch { store.recentTransactions.collect() }
        backgroundScope.launch { store.refreshRecentTransactionsWhileVisible() }
        assertEquals(RecentQuery(50, dayStart(2026, 3, 30, rome)), repository.requests.receive())

        advanceTimeBy(23.hours)
        runCurrent()
        assertEquals(RecentQuery(50, dayStart(2026, 3, 31, rome)), repository.requests.receive())
    }

    private fun TestScope.clockFrom(start: String): Clock = object : Clock {
        private val initial = Instant.parse(start)
        override fun now(): Instant = initial + testScheduler.currentTime.milliseconds
    }

    private fun dayStart(year: Int, month: Int, day: Int, timeZone: TimeZone = zone): Long =
        LocalDate(year, month, day).atStartOfDayIn(timeZone).toEpochMilliseconds()
}

private data class RecentQuery(val limit: Int, val expensesBeforeExclusiveMillis: Long?)

private class RecordingDashboardRepository : DashboardReadRepository {
    val requests = Channel<RecentQuery>(Channel.UNLIMITED)

    override fun getDashboardMonthSummary(year: Int, month: Int) = flowOf(emptyDashboardMonthSummary())
    override fun getDashboardBalanceTrend(selectedYear: Int, selectedMonth: Int, trailingMonthCount: Int) =
        flowOf(DashboardBalanceTrend(expenseTotalsByMonth = emptyList(), incomeTotalsByMonth = emptyList()))
    override fun getDashboardRecentTransactions(limit: Int, expensesBeforeExclusiveMillis: Long?) =
        flowOf(emptyList<DashboardRecentTransaction>()).also {
            requests.trySend(RecentQuery(limit, expensesBeforeExclusiveMillis)).getOrThrow()
        }
    override fun getRecurringExpenseOverviewForMonth(year: Int, month: Int) =
        flowOf(RecurringExpenseOverview(expenses = emptyList(), totalAmount = 0L, nextOccurrenceDate = null))
    override suspend fun getWidgetMonthSummary(year: Int, month: Int) = WidgetMonthSummary(0L, 0L)
}
