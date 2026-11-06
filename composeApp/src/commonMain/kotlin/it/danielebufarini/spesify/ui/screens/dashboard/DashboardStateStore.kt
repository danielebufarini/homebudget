package it.danielebufarini.spesify.ui.screens.dashboard

import it.danielebufarini.spesify.data.DashboardBalanceTrend
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.data.RecurringExpenseOverview
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DashboardStateStore(
    private val repository: DashboardReadRepository,
    initialMonth: MonthCursor,
    scope: CoroutineScope,
    hideFutureExpenses: StateFlow<Boolean>,
    private val clock: Clock = Clock.System,
    recentTransactionsDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault
) {
    private val monthSelection = MonthSelection(initialMonth)
    private val recentTransactionsDayEnd = MutableStateFlow(nextDayStart(clock.now(), timeZone()))

    val selectedMonth: StateFlow<MonthCursor> = monthSelection.readOnly

    val summary: StateFlow<MonthlySummary> = selectedMonth
        .flatMapLatest { month ->
            repository.getDashboardMonthSummary(
                year = month.year,
                month = month.month
            )
        }
        .map { summary -> summary.toUiMonthlySummary() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyDashboardMonthSummary().toUiMonthlySummary()
        )

    private val balanceTrend: SharedFlow<Pair<MonthCursor, DashboardBalanceTrend>> = selectedMonth
        .flatMapLatest { month ->
            repository.getDashboardBalanceTrend(
                selectedYear = month.year,
                selectedMonth = month.month,
                trailingMonthCount = BALANCE_CHART_MONTH_COUNT
            ).map { balanceTrend -> month to balanceTrend }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    val chartState: StateFlow<BalanceChartState> = balanceTrend
        .map { (month, balanceTrend) ->
            buildBalanceChartState(
                balanceTrend = balanceTrend,
                selectedMonth = month
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyBalanceChartState(initialMonth)
        )

    val recentTransactions: StateFlow<List<DashboardRecentTransaction>> = combine(
        hideFutureExpenses,
        recentTransactionsDayEnd
    ) { hideFuture, dayEnd ->
        dayEnd.toEpochMilliseconds().takeIf { hideFuture }
    }
        .flatMapLatest { expensesBeforeExclusiveMillis ->
            repository.getDashboardRecentTransactions(
                limit = DASHBOARD_RECENT_TRANSACTIONS_EXPANDED_LIMIT,
                expensesBeforeExclusiveMillis = expensesBeforeExclusiveMillis
            )
        }
        .distinctUntilChanged()
        .flowOn(recentTransactionsDispatcher)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    val recurringExpenseOverview: StateFlow<RecurringExpenseOverview> = selectedMonth
        .flatMapLatest { month ->
            repository.getRecurringExpenseOverviewForMonth(
                year = month.year,
                month = month.month
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = RecurringExpenseOverview(
                expenses = emptyList(),
                totalAmount = 0L,
                nextOccurrenceDate = null
            )
        )

    fun selectPreviousMonth() {
        monthSelection.previous()
    }

    fun selectNextMonth() {
        monthSelection.next()
    }

    suspend fun refreshRecentTransactionsWhileVisible() {
        while (currentCoroutineContext().isActive) {
            val now = clock.now()
            val dayEnd = nextDayStart(now, timeZone())
            recentTransactionsDayEnd.value = dayEnd
            delay(dayEnd - now)
        }
    }

    // Explicit fields require a non-private property. This private owner keeps the mutable
    // implementation inaccessible while selectedMonth retains its read-only runtime wrapper.
    private class MonthSelection(initialMonth: MonthCursor) {
        val current: StateFlow<MonthCursor>
            field = MutableStateFlow(initialMonth)

        val readOnly: StateFlow<MonthCursor> = current.asStateFlow()

        fun previous() {
            current.update { it.previous() }
        }

        fun next() {
            current.update { it.next() }
        }
    }
}

private fun nextDayStart(now: Instant, timeZone: TimeZone): Instant =
    now.toLocalDateTime(timeZone).date
        .plus(1, DateTimeUnit.DAY)
        .atStartOfDayIn(timeZone)
