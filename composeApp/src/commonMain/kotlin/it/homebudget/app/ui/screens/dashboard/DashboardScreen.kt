package it.homebudget.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.ui.screens.AddExpenseScreen
import it.homebudget.app.ui.screens.CategoriesScreen
import it.homebudget.app.ui.screens.CategoryExpensesScreen
import it.homebudget.app.ui.screens.DayExpensesScreen
import it.homebudget.app.ui.screens.MonthlyExpensesScreen
import it.homebudget.app.ui.screens.MonthlyIncomesScreen
import it.homebudget.app.ui.screens.SharedExpensesScreen

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        DashboardRoute(
            showNavigationChrome = true,
            showFab = false,
            onOpenCategories = { navigator?.push(CategoriesScreen()) },
            onOpenAddExpense = { navigator?.push(AddExpenseScreen()) },
            onOpenDayExpenses = { year, month, day ->
                navigator?.push(DayExpensesScreen(year = year, month = month, day = day))
            },
            onOpenMonthlyIncomes = { year, month ->
                navigator?.push(MonthlyIncomesScreen(year = year, month = month))
            },
            onOpenMonthlyExpenses = { year, month ->
                navigator?.push(MonthlyExpensesScreen(year = year, month = month))
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
            }
        )
    }
}
