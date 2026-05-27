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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.DashboardPreferencesStore
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.subtractAmountsExact
import it.homebudget.app.ui.screens.EnsureStarterCategoriesSeeded
import it.homebudget.app.ui.screens.rememberIsIosPlatform
import org.koin.compose.koinInject

@Composable
fun DashboardRoute(
    showNavigationChrome: Boolean,
    openVoiceExpenseRequest: Int = 0,
    showFab: Boolean,
    showTransactionSearch: Boolean = true,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit = {},
    onOpenCsvTransfer: (() -> Unit)? = null,
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit,
    onOpenTransactionSearch: (Int, Int, String) -> Unit = { _, _, _ -> }
) {
    val repository: ExpenseRepository = koinInject()
    val dashboardPreferencesStore: DashboardPreferencesStore = koinInject()
    val strings = rememberDashboardStrings()
    val pinnedDashboardCard by dashboardPreferencesStore.pinnedDashboardCard.collectAsState()

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
    val dashboardScope = rememberCoroutineScope()
    val dashboardStore = remember(repository, dashboardScope, currentMonth) {
        DashboardStateStore(
            repository = repository,
            initialMonth = currentMonth,
            scope = dashboardScope
        )
    }

    val selectedMonth by dashboardStore.selectedMonth.collectAsState()
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }
    val submitSearch = {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotEmpty()) {
            onOpenTransactionSearch(selectedMonth.year, selectedMonth.month, trimmedQuery)
        }
    }

    EnsureStarterCategoriesSeeded(repository)

    val summary by dashboardStore.summary.collectAsState()
    val chartState by dashboardStore.chartState.collectAsState()
    val recentTransactions by dashboardStore.recentTransactions.collectAsState()

    val monthlySavingsAmount = remember(summary.incomeAmount, summary.totalAmount) {
        subtractAmountsExact(summary.incomeAmount, summary.totalAmount)
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            strings = strings,
            showMonthHeaderCard = !showNavigationChrome,
            selectedMonth = selectedMonth,
            summary = summary,
            monthlySavingsAmount = monthlySavingsAmount,
            chartState = chartState,
            recentTransactions = recentTransactions,
            pinnedDashboardCard = pinnedDashboardCard,
            onPinDashboardCard = dashboardPreferencesStore::pinDashboardCard,
            categoriesById = categoriesById,
            showTransactionSearch = showTransactionSearch,
            searchQuery = searchQuery,
            onPreviousMonth = dashboardStore::selectPreviousMonth,
            onNextMonth = dashboardStore::selectNextMonth,
            onSearchQueryChange = { searchQuery = it },
            onSearchSubmit = submitSearch,
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
            onOpenVoiceExpense = onOpenVoiceExpense,
            onOpenCsvTransfer = onOpenCsvTransfer,
            onPreviousMonth = dashboardStore::selectPreviousMonth,
            onNextMonth = dashboardStore::selectNextMonth
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
