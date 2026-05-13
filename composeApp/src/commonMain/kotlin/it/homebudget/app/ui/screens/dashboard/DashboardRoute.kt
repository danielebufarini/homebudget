package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.addAmountsExact
import it.homebudget.app.ui.screens.EnsureDefaultCategoriesInserted
import it.homebudget.app.ui.screens.rememberIsIosPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@Composable
fun DashboardRoute(
    showNavigationChrome: Boolean,
    openVoiceExpenseRequest: Int = 0,
    showFab: Boolean,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit
) {
    val repository: ExpenseRepository = koinInject()
    val strings = rememberDashboardStrings()

    val categoriesFlow = remember(repository) {
        repository.getAllCategories()
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val categoriesById = remember(categories) {
        categories.associateBy { it.id }
    }

    val currentMonth = remember {
        currentMonthCursor()
    }

    var selectedMonth by remember {
        mutableStateOf(currentMonth)
    }

    EnsureDefaultCategoriesInserted(repository)

    val summaryFlow = remember(repository, selectedMonth) {
        repository.getDashboardMonthSummary(selectedMonth.year, selectedMonth.month)
            .map { summary -> summary.toUiMonthlySummary() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
    val summary by summaryFlow.collectAsState(initial = emptyDashboardMonthSummary().toUiMonthlySummary())

    val chartStateFlow = remember(repository, selectedMonth) {
        repository.getDashboardCashFlow(
            selectedYear = selectedMonth.year,
            selectedMonth = selectedMonth.month,
            trailingMonthCount = CASH_FLOW_CHART_MONTH_COUNT
        )
            .map { cashFlow ->
                buildCashFlowChartState(
                    cashFlow = cashFlow,
                    selectedMonth = selectedMonth
                )
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
    val chartState by chartStateFlow.collectAsState(initial = emptyLineChartState(selectedMonth))

    val sixMonthSavingsAmount = remember(chartState.monthSnapshots) {
        chartState.monthSnapshots.fold(0L) { total, month ->
            addAmountsExact(total, month.differenceAmount)
        }
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            strings = strings,
            showMonthHeaderCard = !showNavigationChrome,
            selectedMonth = selectedMonth,
            summary = summary,
            sixMonthSavingsAmount = sixMonthSavingsAmount,
            chartState = chartState,
            categoriesById = categoriesById,
            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
            onNextMonth = { selectedMonth = selectedMonth.next() },
            onOpenMonthlyIncomes = {
                onOpenMonthlyIncomes(selectedMonth.year, selectedMonth.month)
            },
            onOpenMonthlyExpenses = {
                onOpenMonthlyExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenDayExpenses = { day ->
                onOpenDayExpenses(selectedMonth.year, selectedMonth.month, day)
            },
            onOpenSharedExpenses = {
                onOpenSharedExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenCategoryExpenses = { categoryName ->
                onOpenCategoryExpenses(selectedMonth.year, selectedMonth.month, categoryName)
            }
        )
    }

    if (showNavigationChrome) {
        DashboardScreenScaffold(
            strings = strings,
            openVoiceExpenseRequest = openVoiceExpenseRequest,
            selectedMonth = selectedMonth,
            totalAmount = summary.totalAmount,
            showFab = showFab,
            onOpenCategories = onOpenCategories,
            onOpenAddExpense = onOpenAddExpense,
            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
            onNextMonth = { selectedMonth = selectedMonth.next() }
        ) { modifier ->
            dashboardBody(modifier)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            dashboardBody(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )

            if (showFab) {
                FloatingActionButton(
                    onClick = onOpenAddExpense,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    if (rememberIsIosPlatform()) {
                        Text("+")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = strings.addExpense
                        )
                    }
                }
            }
        }
    }
}
