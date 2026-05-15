package it.homebudget.app

import androidx.compose.ui.window.ComposeUIViewController
import it.homebudget.app.di.initKoin
import it.homebudget.app.ui.screens.AddExpenseScreen
import it.homebudget.app.ui.screens.AddIncomeScreen
import it.homebudget.app.ui.screens.AddTransactionScreen
import it.homebudget.app.ui.screens.CategoryExpensesScreen
import it.homebudget.app.ui.screens.DayExpensesScreen
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.MonthlyExpensesScreen
import it.homebudget.app.ui.screens.MonthlyIncomesScreen
import it.homebudget.app.ui.screens.SharedExpensesScreen
import it.homebudget.app.ui.screens.TransactionEditorKind
import it.homebudget.app.ui.screens.categories.management.CategoriesManagementRoute
import it.homebudget.app.ui.screens.dashboard.DashboardRoute
import it.homebudget.app.ui.screens.startIosGroupedExpensesStore
import it.homebudget.app.ui.theme.AppTheme
import org.koin.mp.KoinPlatformTools

fun MainViewController() = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) { App() }

fun DashboardContentViewController(
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenExpenseDetails: (String, Boolean) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        DashboardRoute(
            showNavigationChrome = false,
            showFab = false,
            onOpenCategories = onOpenCategories,
            onOpenAddExpense = onOpenAddExpense,
            onOpenDayExpenses = onOpenDayExpenses,
            onOpenMonthlyIncomes = onOpenMonthlyIncomes,
            onOpenMonthlyExpenses = onOpenMonthlyExpenses,
            onOpenSharedExpenses = onOpenSharedExpenses,
            onOpenCategoryExpenses = onOpenCategoryExpenses
        )
    }
}

fun AddTransactionViewController(
    initialIncomeSelected: Boolean = false,
    initialIncomeYear: Int? = null,
    initialIncomeMonth: Int? = null,
    onClose: () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
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
}

fun AddExpenseViewController(
    expenseId: String? = null,
    readOnly: Boolean = false,
    onClose: () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        AddExpenseScreen(expenseId = expenseId, readOnly = readOnly).RouteContent(
            showNavigationChrome = false,
            onClose = onClose
        )
    }
}

fun AddIncomeViewController(
    incomeId: String? = null,
    initialYear: Int? = null,
    initialMonth: Int? = null,
    onClose: () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        AddIncomeScreen(
            incomeId = incomeId,
            initialYear = initialYear,
            initialMonth = initialMonth
        ).RouteContent(
            showNavigationChrome = false,
            onClose = onClose
        )
    }
}

fun CategoriesViewController(
    onClose: () -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        CategoriesManagementRoute(onBack = null)
    }
}

fun MonthlyExpensesViewController(
    year: Int,
    month: Int,
    onOpenExpense: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        MonthlyExpensesScreen(year = year, month = month).RouteContent(
            showNavigationChrome = false,
            onBack = {},
            onAddExpense = {},
            onOpenExpense = onOpenExpense
        )
    }
}

fun DayExpensesViewController(
    year: Int,
    month: Int,
    day: Int,
    onOpenExpense: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        DayExpensesScreen(year = year, month = month, day = day).RouteContent(
            showNavigationChrome = false,
            onBack = {},
            onOpenExpense = onOpenExpense
        )
    }
}

fun MonthlyIncomesViewController(
    year: Int,
    month: Int,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        MonthlyIncomesScreen(year = year, month = month).RouteContent(
            initialMonth = MonthCursor(year = year, month = month),
            showNavigationChrome = false,
            onBack = {},
            onAddIncome = { _, _ -> onAddIncome() },
            onOpenIncome = onOpenIncome
        )
    }
}

fun SharedExpensesViewController(
    year: Int,
    month: Int,
    onOpenExpense: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        SharedExpensesScreen(year = year, month = month).RouteContent(
            showNavigationChrome = false,
            onBack = {},
            onAddExpense = {},
            onOpenExpense = onOpenExpense
        )
    }
}

fun CategoryExpensesViewController(
    year: Int,
    month: Int,
    categoryName: String,
    onOpenExpense: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        CategoryExpensesScreen(year = year, month = month, categoryName = categoryName).RouteContent(
            showNavigationChrome = false,
            onBack = {},
            onAddExpense = {},
            onOpenExpense = onOpenExpense
        )
    }
}

private fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
    startIosGroupedExpensesStore()
}
