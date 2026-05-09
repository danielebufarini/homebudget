package it.homebudget.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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

    override fun showMonthNavigationControls(): Boolean = false

    @Composable
    override fun sectionHeaderContainerColor(): Color = MaterialTheme.colorScheme.primaryContainer

    @Composable
    override fun sectionHeaderContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun sectionHeaderTextStyle(): TextStyle = MaterialTheme.typography.titleMedium

    @Composable
    override fun sectionHeaderIconTint(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun sectionHeaderChevronContainerColor(): Color =
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)

    @Composable
    override fun sectionHeaderChevronContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun monthNavigationDescriptor(): String = categoryName
}
