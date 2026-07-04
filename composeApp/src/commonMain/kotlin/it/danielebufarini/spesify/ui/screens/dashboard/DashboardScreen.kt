package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.spesify.ui.screens.categories.CategoryExpensesScreen
import it.danielebufarini.spesify.ui.screens.categories.management.CategoriesManagementScreen
import it.danielebufarini.spesify.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.spesify.ui.screens.expenses.DayExpensesScreen
import it.danielebufarini.spesify.ui.screens.expenses.SharedExpensesScreen
import it.danielebufarini.spesify.ui.screens.income.AddIncomeScreen
import it.danielebufarini.spesify.ui.screens.recurring.RecurringExpensesScreen
import it.danielebufarini.spesify.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.spesify.ui.screens.transactions.MonthlyTransactionsScreen
import it.danielebufarini.spesify.ui.screens.transactions.TransactionEditorKind

class DashboardScreen(
    private val openVoiceExpenseRequest: Int = 0
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        DashboardRoute(
            showNavigationChrome = true,
            openVoiceExpenseRequest = openVoiceExpenseRequest,
            showFab = false,
            onOpenCategories = { navigator?.push(CategoriesManagementScreen) },
            onOpenAddExpense = { navigator?.push(AddTransactionScreen()) },
            onOpenDayExpenses = { year, month, day ->
                navigator?.push(DayExpensesScreen(year = year, month = month, day = day))
            },
            onOpenMonthlyIncomes = { year, month ->
                navigator?.push(
                    MonthlyTransactionsScreen(
                        year = year,
                        month = month,
                        initialKind = TransactionEditorKind.Income,
                    )
                )
            },
            onOpenMonthlyExpenses = { year, month ->
                navigator?.push(
                    MonthlyTransactionsScreen(
                        year = year,
                        month = month,
                        initialKind = TransactionEditorKind.Expense,
                    )
                )
            },
            onOpenSharedExpenses = { year, month ->
                navigator?.push(SharedExpensesScreen(year = year, month = month))
            },
            onOpenRecurringExpenses = { year, month ->
                navigator?.push(RecurringExpensesScreen(year = year, month = month))
            },
            onOpenCategoryTransactions = { year, month, categoryId, categoryName ->
                navigator?.push(
                    CategoryExpensesScreen(
                        year = year,
                        month = month,
                        categoryName = categoryName,
                        categoryId = categoryId
                    )
                )
            },
            onOpenExpenseDetails = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId = expenseId, readOnly = false))
            },
            onOpenIncomeDetails = { incomeId ->
                navigator?.push(AddIncomeScreen(incomeId = incomeId))
            },
            onOpenTransactionSearch = { year, month, query ->
                navigator?.push(
                    MonthlyTransactionsScreen(
                        year = year,
                        month = month,
                        initialSearchQuery = query,
                    )
                )
            }
        )
    }
}
