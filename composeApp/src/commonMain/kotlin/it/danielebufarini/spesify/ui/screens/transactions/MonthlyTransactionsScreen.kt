package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.ui.screens.TransactionTotals
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.common.MonthNavigationTitle
import it.danielebufarini.spesify.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.spesify.ui.screens.expenses.GroupingModeButtons
import it.danielebufarini.spesify.ui.screens.expenses.MonthlyExpensesScreen
import it.danielebufarini.spesify.ui.screens.income.AddIncomeScreen
import it.danielebufarini.spesify.ui.screens.income.MonthlyIncomesScreen
import it.danielebufarini.spesify.ui.screens.monthSwipeNavigation
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.spesify.ui.screens.rememberMonthlyTransactionsRouteState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.add_expense
import spesify.composeapp.generated.resources.add_income
import spesify.composeapp.generated.resources.back
import spesify.composeapp.generated.resources.category
import spesify.composeapp.generated.resources.currency_symbol
import spesify.composeapp.generated.resources.date
import spesify.composeapp.generated.resources.expense
import spesify.composeapp.generated.resources.expenses
import spesify.composeapp.generated.resources.income
import spesify.composeapp.generated.resources.search_results

class MonthlyTransactionsScreen(
    private val year: Int,
    private val month: Int,
    private val initialKind: TransactionEditorKind = TransactionEditorKind.Expense,
    private val initialSearchQuery: String = "",
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddExpense = {
                navigator?.push(AddTransactionScreen(initialKind = TransactionEditorKind.Expense))
            },
            onAddIncome = { selectedYear, selectedMonth ->
                navigator?.push(
                    AddTransactionScreen(
                        initialKind = TransactionEditorKind.Income,
                        initialIncomeYear = selectedYear,
                        initialIncomeMonth = selectedMonth,
                    )
                )
            },
            onOpenExpense = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId))
            },
            onOpenIncome = { incomeId ->
                navigator?.push(AddIncomeScreen(incomeId))
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddExpense: () -> Unit,
        onAddIncome: (Int, Int) -> Unit,
        onOpenExpense: (String) -> Unit,
        onOpenIncome: (String) -> Unit,
    ) {
        val dashboardReadRepository: DashboardReadRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val addIncomeLabel = stringResource(Res.string.add_income)
        val backLabel = stringResource(Res.string.back)
        val categoryLabel = stringResource(Res.string.category)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val dateLabel = stringResource(Res.string.date)
        val expenseLabel = stringResource(Res.string.expense)
        val expensesLabel = stringResource(Res.string.expenses)
        val incomeLabel = stringResource(Res.string.income)
        val searchResultsLabel = stringResource(Res.string.search_results)
        val searchQuery = remember(initialSearchQuery) { initialSearchQuery.trim() }
        val searchMode = searchQuery.isNotBlank()
        val routeState = rememberMonthlyTransactionsRouteState(
            initialMonth = MonthCursor(year, month),
            initialKind = initialKind,
            searchQuery = searchQuery,
        )
        val totals by if (searchMode) {
            remember(searchQuery) {
                flowOf(TransactionTotals())
            }
        } else {
            remember(dashboardReadRepository, routeState.selectedMonth) {
                dashboardReadRepository.getDashboardMonthSummary(
                    routeState.selectedMonth.year,
                    routeState.selectedMonth.month,
                )
                    .map { summary ->
                        TransactionTotals(
                            expenseAmount = summary.totalAmount,
                            incomeAmount = summary.incomeAmount,
                        )
                    }
                    .distinctUntilChanged()
            }
        }.collectAsState(initial = TransactionTotals())
        val totalAmount = when (routeState.selectedKind) {
            TransactionEditorKind.Expense -> totals.expenseAmount
            TransactionEditorKind.Income -> totals.incomeAmount
        }
        val descriptor = when (routeState.selectedKind) {
            TransactionEditorKind.Expense -> expensesLabel
            TransactionEditorKind.Income -> incomeLabel
        }
        val addContentDescription = when (routeState.selectedKind) {
            TransactionEditorKind.Expense -> addExpenseLabel
            TransactionEditorKind.Income -> addIncomeLabel
        }

        Scaffold(
            containerColor = if (isIos) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars,
            topBar = {
                if (showNavigationChrome) {
                    CenterAlignedTopAppBar(
                        title = {
                            if (searchMode) {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(searchResultsLabel)
                                    Text(
                                        text = "\"$searchQuery\" • $descriptor",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                MonthNavigationTitle(
                                    selectedMonth = routeState.selectedMonth,
                                    subtitle = "$descriptor • ${formatAmount(totalAmount, currencySymbol)}",
                                    onPreviousMonth = routeState::previousMonth,
                                    onNextMonth = routeState::nextMonth,
                                )
                            }
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(backLabel)
                                }
                            }
                        },
                        actions = {
                            if (!isIos) {
                                BottomTransactionQuickActions(
                                    addContentDescription = addContentDescription,
                                    onAddTransaction = {
                                        when (routeState.selectedKind) {
                                            TransactionEditorKind.Expense -> onAddExpense()
                                            TransactionEditorKind.Income -> {
                                                onAddIncome(
                                                    routeState.selectedMonth.year,
                                                    routeState.selectedMonth.month,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TransactionKindSelector(
                    selectedKind = routeState.selectedKind,
                    expenseLabel = expenseLabel,
                    incomeLabel = incomeLabel,
                    onKindSelected = routeState::selectKind,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                )

                GroupingModeButtons(
                    groupingMode = routeState.groupingMode,
                    onGroupingModeChange = routeState::selectGroupingMode,
                    byCategoryLabel = categoryLabel,
                    byDateLabel = dateLabel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .monthSwipeNavigation(
                            enabled = !searchMode,
                            onPreviousMonth = routeState::previousMonth,
                            onNextMonth = routeState::nextMonth
                        ),
                ) {
                    when (routeState.selectedKind) {
                        TransactionEditorKind.Expense -> MonthlyExpensesScreen(
                            year = routeState.selectedMonth.year,
                            month = routeState.selectedMonth.month,
                            searchQuery = searchQuery,
                            searchPageCount = routeState.searchPageCount,
                            onLoadMoreSearchResults = routeState::loadNextSearchPage,
                        ).RouteContent(
                            showNavigationChrome = false,
                            onBack = onBack,
                            onAddExpense = onAddExpense,
                            onOpenExpense = onOpenExpense,
                            externalGroupingMode = routeState.groupingMode,
                            onExternalGroupingModeChange = routeState::selectGroupingMode,
                            showGroupingControls = false,
                        )

                        TransactionEditorKind.Income -> MonthlyIncomesScreen(
                            year = routeState.selectedMonth.year,
                            month = routeState.selectedMonth.month,
                            searchQuery = searchQuery,
                            externalSearchPageCount = routeState.searchPageCount,
                            onLoadMoreSearchResults = routeState::loadNextSearchPage,
                        ).RouteContent(
                            initialMonth = routeState.selectedMonth,
                            showNavigationChrome = false,
                            onBack = onBack,
                            onAddIncome = onAddIncome,
                            onOpenIncome = onOpenIncome,
                            externalGroupingMode = routeState.groupingMode,
                            onExternalGroupingModeChange = routeState::selectGroupingMode,
                            showGroupingControls = false,
                        )
                    }
                }
            }
        }
    }
}
