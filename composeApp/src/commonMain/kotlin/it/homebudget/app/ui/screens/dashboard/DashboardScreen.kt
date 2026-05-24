package it.homebudget.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.ui.screens.AddTransactionScreen
import it.homebudget.app.ui.screens.CategoryExpensesScreen
import it.homebudget.app.ui.screens.DayExpensesScreen
import it.homebudget.app.ui.screens.MonthlyTransactionsScreen
import it.homebudget.app.ui.screens.SharedExpensesScreen
import it.homebudget.app.ui.screens.TransactionEditorKind
import it.homebudget.app.ui.screens.categories.management.CategoriesManagementScreen

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
