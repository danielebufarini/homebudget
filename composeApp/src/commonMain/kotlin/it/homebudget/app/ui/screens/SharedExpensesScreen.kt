package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.no_shared_expenses_for_month
import homebudget.composeapp.generated.resources.shared_expense
import homebudget.composeapp.generated.resources.shared_expenses
import it.homebudget.app.database.Expense
import org.jetbrains.compose.resources.stringResource

class SharedExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    @Composable
    override fun screenTitle(monthName: String): String = "$monthName ${stringResource(Res.string.shared_expenses)}"

    @Composable
    override fun emptyStateText(): String = stringResource(Res.string.no_shared_expenses_for_month)

    @Composable
    override fun expenseFallbackTitle(): String = stringResource(Res.string.shared_expense)

    override fun includeExpense(expense: Expense): Boolean = expense.isShared == 1L

    override fun centerAlignedTitle(): Boolean = true

    @Composable
    override fun monthNavigationDescriptor(): String = stringResource(Res.string.shared_expenses)
}
