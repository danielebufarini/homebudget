package it.danielebufarini.spesify

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import it.danielebufarini.spesify.di.initKoin
import it.danielebufarini.spesify.ui.screens.categories.CategoryExpensesScreen
import it.danielebufarini.spesify.ui.screens.categories.management.CategoriesManagementRoute
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.dashboard.DashboardRoute
import it.danielebufarini.spesify.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.spesify.ui.screens.expenses.DayExpensesScreen
import it.danielebufarini.spesify.ui.screens.expenses.MonthlyExpensesScreen
import it.danielebufarini.spesify.ui.screens.expenses.SharedExpensesScreen
import it.danielebufarini.spesify.ui.screens.income.AddIncomeScreen
import it.danielebufarini.spesify.ui.screens.income.MonthlyIncomesScreen
import it.danielebufarini.spesify.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.spesify.ui.screens.transactions.MonthlyTransactionsScreen
import it.danielebufarini.spesify.ui.screens.transactions.TransactionEditorKind
import it.danielebufarini.spesify.ui.theme.AppTheme
import org.koin.mp.KoinPlatformTools

fun MainViewController() = spesifyComposeViewController { App() }

fun DashboardContentViewController(
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit,
    onOpenCsvTransfer: () -> Unit,
    onNavigationDrawerVisibilityChange: (Boolean) -> Unit,
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenExpenseDetails: (String, Boolean) -> Unit,
    onOpenIncomeDetails: (String) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit,
    onOpenTransactionSearch: (Int, Int, String) -> Unit
) = themedSpesifyComposeViewController {
    DashboardRoute(
        showNavigationChrome = true,
        showFab = false,
        showQuickActions = false,
        onOpenCategories = onOpenCategories,
        onOpenAddExpense = onOpenAddExpense,
        onOpenVoiceExpense = onOpenVoiceExpense,
        onOpenCsvTransfer = onOpenCsvTransfer,
        onNavigationDrawerVisibilityChange = onNavigationDrawerVisibilityChange,
        onOpenDayExpenses = onOpenDayExpenses,
        onOpenMonthlyIncomes = onOpenMonthlyIncomes,
        onOpenMonthlyExpenses = onOpenMonthlyExpenses,
        onOpenSharedExpenses = onOpenSharedExpenses,
        onOpenCategoryExpenses = onOpenCategoryExpenses,
        onOpenExpenseDetails = { expenseId -> onOpenExpenseDetails(expenseId, true) },
        onOpenIncomeDetails = onOpenIncomeDetails,
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
    CategoriesManagementRoute(onBack = null)
}

fun MonthlyExpensesViewController(
    year: Int,
    month: Int,
    onOpenExpense: (String) -> Unit
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
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
) = themedSpesifyComposeViewController {
    CategoryExpensesScreen(year = year, month = month, categoryName = categoryName).RouteContent(
        showNavigationChrome = false,
        onBack = {},
        onAddExpense = {},
        onOpenExpense = onOpenExpense
    )
}

private fun spesifyComposeViewController(
    content: @Composable () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    content()
}

private fun themedSpesifyComposeViewController(
    content: @Composable () -> Unit
) = spesifyComposeViewController {
    AppTheme {
        content()
    }
}

private fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
