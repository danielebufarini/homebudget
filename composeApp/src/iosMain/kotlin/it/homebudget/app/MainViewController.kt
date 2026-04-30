package it.homebudget.app

import androidx.compose.ui.window.ComposeUIViewController
import it.homebudget.app.di.initKoin
import it.homebudget.app.localization.ProvideAppStrings
import it.homebudget.app.ui.screens.*
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
    ProvideAppStrings {
        AppTheme {
            DashboardRoute(
                showNavigationChrome = false,
                showFab = false,
                onOpenCategories = onOpenCategories,
                onOpenAddExpense = onOpenAddExpense,
                onOpenMonthlyIncomes = onOpenMonthlyIncomes,
                onOpenMonthlyExpenses = onOpenMonthlyExpenses,
                onOpenSharedExpenses = onOpenSharedExpenses,
                onOpenExpenseDetails = onOpenExpenseDetails,
                onOpenCategoryExpenses = onOpenCategoryExpenses
            )
        }
    }
}

fun CategoriesContentViewController(
    addCategoryRequestKey: Int = 0
) = ComposeUIViewController(
    configure = {
        ensureKoinStarted()
    }
) {
    ProvideAppStrings {
        AppTheme {
            CategoriesRoute(
                onBack = {},
                showNavigationChrome = false,
                showFab = false,
                addCategoryRequestKey = addCategoryRequestKey
            )
        }
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
    ProvideAppStrings {
        AppTheme {
            AddExpenseScreen(expenseId = expenseId, readOnly = readOnly).RouteContent(
                showNavigationChrome = false,
                onClose = onClose
            )
        }
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
    ProvideAppStrings {
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
    ProvideAppStrings {
        AppTheme {
            MonthlyExpensesScreen(year = year, month = month).RouteContent(
                showNavigationChrome = false,
                onBack = {},
                onAddExpense = {},
                onOpenExpense = onOpenExpense
            )
        }
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
    ProvideAppStrings {
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
    ProvideAppStrings {
        AppTheme {
            SharedExpensesScreen(year = year, month = month).RouteContent(
                showNavigationChrome = false,
                onBack = {},
                onAddExpense = {},
                onOpenExpense = onOpenExpense
            )
        }
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
    ProvideAppStrings {
        AppTheme {
            CategoryExpensesScreen(year = year, month = month, categoryName = categoryName).RouteContent(
                showNavigationChrome = false,
                onBack = {},
                onAddExpense = {},
                onOpenExpense = onOpenExpense
            )
        }
    }
}

private fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
    startIosGroupedExpensesStore()
}
