package it.homebudget.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import it.homebudget.app.di.initKoin
import it.homebudget.app.ui.screens.AddExpenseScreen
import it.homebudget.app.ui.screens.CategoriesRoute
import it.homebudget.app.ui.screens.CategoryExpensesScreen
import it.homebudget.app.ui.screens.DashboardRoute
import it.homebudget.app.ui.screens.startIosGroupedExpensesStore
import it.homebudget.app.ui.screens.MonthlyExpensesScreen
import it.homebudget.app.ui.screens.SharedExpensesScreen
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
            onOpenMonthlyExpenses = onOpenMonthlyExpenses,
            onOpenSharedExpenses = onOpenSharedExpenses,
            onOpenExpenseDetails = onOpenExpenseDetails,
            onOpenCategoryExpenses = onOpenCategoryExpenses
        )
    }
}

fun CategoriesContentViewController(
    addCategoryRequestKey: Int = 0
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    AppTheme {
        CategoriesRoute(
            onBack = {},
            showNavigationChrome = false,
            showFab = false,
            addCategoryRequestKey = addCategoryRequestKey
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
            onOpenExpense = onOpenExpense
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
