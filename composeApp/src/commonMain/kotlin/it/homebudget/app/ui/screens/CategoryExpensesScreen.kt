package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.no_expenses_for_category_this_month
import it.homebudget.app.database.Expense
import org.jetbrains.compose.resources.stringResource

class CategoryExpensesScreen(
    year: Int,
    month: Int,
    private val categoryName: String
) : BaseGroupedExpensesScreen(year, month) {

    @Composable
    override fun screenTitle(monthName: String): String = "$monthName $categoryName"

    @Composable
    override fun emptyStateText(): String =
        stringResource(Res.string.no_expenses_for_category_this_month, categoryName)

    @Composable
    override fun expenseFallbackTitle(): String = stringResource(Res.string.expense)

    override fun includeExpense(expense: Expense): Boolean = true

    override fun includeCategory(categoryName: String): Boolean = categoryName == this.categoryName

    override fun groupsExpandedByDefault(): Boolean = true
}
