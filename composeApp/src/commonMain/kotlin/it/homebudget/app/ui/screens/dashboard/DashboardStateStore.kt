package it.homebudget.app.ui.screens.dashboard

import it.homebudget.app.data.DashboardCashFlow
import it.homebudget.app.data.DashboardRecentTransaction
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.ui.screens.MonthCursor
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
    private val repository: ExpenseRepository,
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

    private val cashFlow: SharedFlow<Pair<MonthCursor, DashboardCashFlow>> = monthCursor
        .flatMapLatest { month ->
            repository.getDashboardCashFlow(
                selectedYear = month.year,
                selectedMonth = month.month,
                trailingMonthCount = CASH_FLOW_CHART_MONTH_COUNT
            ).map { cashFlow -> month to cashFlow }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    val chartState: StateFlow<LineChartState> = cashFlow
        .map { (month, cashFlow) ->
            buildCashFlowChartState(
                cashFlow = cashFlow,
                selectedMonth = month
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyLineChartState(initialMonth)
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
