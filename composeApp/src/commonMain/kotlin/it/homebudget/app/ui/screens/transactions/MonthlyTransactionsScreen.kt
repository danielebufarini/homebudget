package it.homebudget.app.ui.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.expenses
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.search_results
import it.homebudget.app.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val addIncomeLabel = stringResource(Res.string.add_income)
        val backLabel = stringResource(Res.string.back)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val expenseLabel = stringResource(Res.string.expense)
        val expensesLabel = stringResource(Res.string.expenses)
        val incomeLabel = stringResource(Res.string.income)
        val searchResultsLabel = stringResource(Res.string.search_results)
        val searchQuery = remember(initialSearchQuery) { initialSearchQuery.trim() }
        val searchMode = searchQuery.isNotBlank()

        var selectedMonth by remember(year, month) { mutableStateOf(MonthCursor(year, month)) }
        var selectedKind by remember(initialKind) { mutableStateOf(initialKind) }
        var searchPage by remember(searchQuery) { mutableStateOf(1) }
        val searchCandidateLimit = searchPage * DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
        val loadMoreSearchResults = {
            searchPage += 1
        }
        val totals by if (searchMode) {
            remember(searchQuery) {
                flowOf(TransactionTotals())
            }
        } else {
            remember(repository, selectedMonth) {
                repository.getDashboardMonthSummary(selectedMonth.year, selectedMonth.month)
                    .map { summary ->
                        TransactionTotals(
                            expenseAmount = summary.totalAmount,
                            incomeAmount = summary.incomeAmount,
                        )
                    }
                    .distinctUntilChanged()
            }
        }.collectAsState(initial = TransactionTotals())
        val totalAmount = when (selectedKind) {
            TransactionEditorKind.Expense -> totals.expenseAmount
            TransactionEditorKind.Income -> totals.incomeAmount
        }
        val descriptor = when (selectedKind) {
            TransactionEditorKind.Expense -> expensesLabel
            TransactionEditorKind.Income -> incomeLabel
        }
        val addContentDescription = when (selectedKind) {
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
                                    selectedMonth = selectedMonth,
                                    subtitle = "$descriptor • ${formatAmount(totalAmount, currencySymbol)}",
                                    onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                    onNextMonth = { selectedMonth = selectedMonth.next() },
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
                                        when (selectedKind) {
                                            TransactionEditorKind.Expense -> onAddExpense()
                                            TransactionEditorKind.Income -> {
                                                onAddIncome(selectedMonth.year, selectedMonth.month)
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
                    selectedKind = selectedKind,
                    expenseLabel = expenseLabel,
                    incomeLabel = incomeLabel,
                    onKindSelected = { selectedKind = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .monthSwipeNavigation(
                            enabled = !searchMode,
                            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                            onNextMonth = { selectedMonth = selectedMonth.next() }
                        ),
                ) {
                    when (selectedKind) {
                        TransactionEditorKind.Expense -> MonthlyExpensesScreen(
                            year = selectedMonth.year,
                            month = selectedMonth.month,
                            searchQuery = searchQuery,
                            searchCandidateLimit = searchCandidateLimit,
                            onLoadMoreSearchResults = loadMoreSearchResults,
                        ).RouteContent(
                            showNavigationChrome = false,
                            onBack = onBack,
                            onAddExpense = onAddExpense,
                            onOpenExpense = onOpenExpense,
                        )

                        TransactionEditorKind.Income -> MonthlyIncomesScreen(
                            year = selectedMonth.year,
                            month = selectedMonth.month,
                            searchQuery = searchQuery,
                            externalSearchCandidateLimit = searchCandidateLimit,
                            onLoadMoreSearchResults = loadMoreSearchResults,
                        ).RouteContent(
                            initialMonth = selectedMonth,
                            showNavigationChrome = false,
                            onBack = onBack,
                            onAddIncome = onAddIncome,
                            onOpenIncome = onOpenIncome,
                        )
                    }
                }
            }
        }
    }
}
