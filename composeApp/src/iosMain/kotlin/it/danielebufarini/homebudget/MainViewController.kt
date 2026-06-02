package it.danielebufarini.homebudget

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import it.danielebufarini.homebudget.di.initKoin
import it.danielebufarini.homebudget.ui.screens.categories.CategoryExpensesScreen
import it.danielebufarini.homebudget.ui.screens.categories.management.CategoriesManagementRoute
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import it.danielebufarini.homebudget.ui.screens.dashboard.DashboardRoute
import it.danielebufarini.homebudget.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.homebudget.ui.screens.expenses.DayExpensesScreen
import it.danielebufarini.homebudget.ui.screens.expenses.MonthlyExpensesScreen
import it.danielebufarini.homebudget.ui.screens.expenses.SharedExpensesScreen
import it.danielebufarini.homebudget.ui.screens.income.AddIncomeScreen
import it.danielebufarini.homebudget.ui.screens.income.MonthlyIncomesScreen
import it.danielebufarini.homebudget.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.homebudget.ui.screens.transactions.MonthlyTransactionsScreen
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind
import it.danielebufarini.homebudget.ui.theme.AppTheme
import org.koin.mp.KoinPlatformTools

fun MainViewController() = homeBudgetComposeViewController { App() }

fun DashboardContentViewController(
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit,
    onOpenCsvTransfer: () -> Unit,
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenExpenseDetails: (String, Boolean) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit,
    onOpenTransactionSearch: (Int, Int, String) -> Unit
) = themedHomeBudgetComposeViewController {
    DashboardRoute(
        showNavigationChrome = true,
        showFab = false,
        onOpenCategories = onOpenCategories,
        onOpenAddExpense = onOpenAddExpense,
        onOpenVoiceExpense = onOpenVoiceExpense,
        onOpenCsvTransfer = onOpenCsvTransfer,
        onOpenDayExpenses = onOpenDayExpenses,
        onOpenMonthlyIncomes = onOpenMonthlyIncomes,
        onOpenMonthlyExpenses = onOpenMonthlyExpenses,
        onOpenSharedExpenses = onOpenSharedExpenses,
        onOpenCategoryExpenses = onOpenCategoryExpenses,
        onOpenTransactionSearch = onOpenTransactionSearch
    )
}

fun TransactionSearchViewController(
    year: Int,
    month: Int,
    query: String,
    onClose: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onOpenIncome: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    MonthlyTransactionsScreen(
        year = year,
        month = month,
        initialSearchQuery = query
    ).RouteContent(
        showNavigationChrome = true,
        onBack = onClose,
        onAddExpense = {},
        onAddIncome = { _, _ -> },
        onOpenExpense = onOpenExpense,
        onOpenIncome = onOpenIncome
    )
}

fun AddTransactionViewController(
    initialIncomeSelected: Boolean = false,
    initialIncomeYear: Int? = null,
    initialIncomeMonth: Int? = null,
    onClose: () -> Unit
) = themedHomeBudgetComposeViewController {
    AddTransactionScreen(
        initialKind = if (initialIncomeSelected) {
            TransactionEditorKind.Income
        } else {
            TransactionEditorKind.Expense
        },
        initialIncomeYear = initialIncomeYear,
        initialIncomeMonth = initialIncomeMonth
    ).RouteContent(
        showNavigationChrome = false,
        onClose = onClose
    )
}

fun AddExpenseViewController(
    expenseId: String? = null,
    readOnly: Boolean = false,
    useHostedFloatingChrome: Boolean = false,
    onClose: () -> Unit
) = themedHomeBudgetComposeViewController {
    AddExpenseScreen(expenseId = expenseId, readOnly = readOnly).RouteContent(
        showNavigationChrome = false,
        onClose = onClose,
        useHostedFloatingChrome = useHostedFloatingChrome
    )
}

fun AddIncomeViewController(
    incomeId: String? = null,
    initialYear: Int? = null,
    initialMonth: Int? = null,
    useHostedFloatingChrome: Boolean = false,
    onClose: () -> Unit
) = themedHomeBudgetComposeViewController {
    AddIncomeScreen(
        incomeId = incomeId,
        initialYear = initialYear,
        initialMonth = initialMonth
    ).RouteContent(
        showNavigationChrome = false,
        onClose = onClose,
        useHostedFloatingChrome = useHostedFloatingChrome
    )
}

fun CategoriesViewController(
    onClose: () -> Unit
) = themedHomeBudgetComposeViewController {
    CategoriesManagementRoute(onBack = null)
}

fun MonthlyExpensesViewController(
    year: Int,
    month: Int,
    onOpenExpense: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    MonthlyExpensesScreen(year = year, month = month).RouteContent(
        showNavigationChrome = false,
        onBack = {},
        onAddExpense = {},
        onOpenExpense = onOpenExpense
    )
}

fun DayExpensesViewController(
    year: Int,
    month: Int,
    day: Int,
    onOpenExpense: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    DayExpensesScreen(year = year, month = month, day = day).RouteContent(
        showNavigationChrome = false,
        onBack = {},
        onOpenExpense = onOpenExpense
    )
}

fun MonthlyIncomesViewController(
    year: Int,
    month: Int,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    MonthlyIncomesScreen(year = year, month = month).RouteContent(
        initialMonth = MonthCursor(year = year, month = month),
        showNavigationChrome = false,
        onBack = {},
        onAddIncome = { _, _ -> onAddIncome() },
        onOpenIncome = onOpenIncome
    )
}

fun SharedExpensesViewController(
    year: Int,
    month: Int,
    onOpenExpense: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    SharedExpensesScreen(year = year, month = month).RouteContent(
        showNavigationChrome = false,
        onBack = {},
        onAddExpense = {},
        onOpenExpense = onOpenExpense
    )
}

fun CategoryExpensesViewController(
    year: Int,
    month: Int,
    categoryName: String,
    onOpenExpense: (String) -> Unit
) = themedHomeBudgetComposeViewController {
    CategoryExpensesScreen(year = year, month = month, categoryName = categoryName).RouteContent(
        showNavigationChrome = false,
        onBack = {},
        onAddExpense = {},
        onOpenExpense = onOpenExpense
    )
}

private fun homeBudgetComposeViewController(
    content: @Composable () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    content()
}

private fun themedHomeBudgetComposeViewController(
    content: @Composable () -> Unit
) = homeBudgetComposeViewController {
    AppTheme {
        content()
    }
}

private fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
