package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.expenses
import homebudget.composeapp.generated.resources.no_expenses_for_month
import it.homebudget.app.database.Expense
import org.jetbrains.compose.resources.stringResource

class MonthlyExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    @Composable
    override fun screenTitle(monthName: String): String = "$monthName ${stringResource(Res.string.expenses)}"

    @Composable
    override fun emptyStateText(): String = stringResource(Res.string.no_expenses_for_month)

    @Composable
    override fun expenseFallbackTitle(): String = stringResource(Res.string.expense)

    override fun includeExpense(expense: Expense): Boolean = true

    override fun canAddExpense(): Boolean = true

    @Composable
    override fun monthNavigationDescriptor(): String = stringResource(Res.string.expenses)
}
