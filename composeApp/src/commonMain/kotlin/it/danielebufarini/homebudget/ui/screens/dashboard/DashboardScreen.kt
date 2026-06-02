package it.danielebufarini.homebudget.ui.screens.dashboard

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.homebudget.ui.screens.categories.CategoryExpensesScreen
import it.danielebufarini.homebudget.ui.screens.categories.management.CategoriesManagementScreen
import it.danielebufarini.homebudget.ui.screens.expenses.DayExpensesScreen
import it.danielebufarini.homebudget.ui.screens.expenses.SharedExpensesScreen
import it.danielebufarini.homebudget.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.homebudget.ui.screens.transactions.MonthlyTransactionsScreen
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind

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
            onOpenCategoryExpenses = { year, month, categoryName ->
                navigator?.push(
                    CategoryExpensesScreen(
                        year = year,
                        month = month,
                        categoryName = categoryName
                    )
                )
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
