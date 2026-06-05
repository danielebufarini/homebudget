package it.danielebufarini.homebudget.ui.screens.dashboard

import it.danielebufarini.homebudget.data.DashboardBalanceTrend
import it.danielebufarini.homebudget.data.DashboardReadRepository
import it.danielebufarini.homebudget.data.DashboardRecentTransaction
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
internal class DashboardStateStore(
    private val repository: DashboardReadRepository,
    initialMonth: MonthCursor,
    scope: CoroutineScope
) {
    private val monthCursor = MutableStateFlow(initialMonth)

    val selectedMonth: StateFlow<MonthCursor> = monthCursor.asStateFlow()

    val summary: StateFlow<MonthlySummary> = monthCursor
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

    private val balanceTrend: SharedFlow<Pair<MonthCursor, DashboardBalanceTrend>> = monthCursor
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

    val recentTransactions: StateFlow<List<DashboardRecentTransaction>> = repository
        .getDashboardRecentTransactions()
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    fun selectPreviousMonth() {
        monthCursor.value = monthCursor.value.previous()
    }

    fun selectNextMonth() {
        monthCursor.value = monthCursor.value.next()
    }
}
