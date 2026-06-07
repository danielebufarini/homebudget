package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import it.danielebufarini.spesify.database.Expense
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.expense
import spesify.composeapp.generated.resources.expenses
import spesify.composeapp.generated.resources.no_expenses_for_month

class MonthlyExpensesScreen(
    year: Int,
    month: Int,
    searchQuery: String = "",
    searchPageCount: Int? = null,
    onLoadMoreSearchResults: (() -> Unit)? = null
) : BaseGroupedExpensesScreen(
    year = year,
    month = month,
    searchQuery = searchQuery,
    externalSearchPageCount = searchPageCount,
    onLoadMoreSearchResults = onLoadMoreSearchResults
) {

    @Composable
    override fun screenTitle(monthName: String): String = "$monthName ${stringResource(Res.string.expenses)}"

    @Composable
    override fun emptyStateText(): String = stringResource(Res.string.no_expenses_for_month)

    @Composable
    override fun expenseFallbackTitle(): String = stringResource(Res.string.expense)

    override fun includeExpense(expense: Expense): Boolean = true

    override fun canAddExpense(): Boolean = true

    @Composable
    override fun sectionHeaderContainerColor(): Color = MaterialTheme.colorScheme.primaryContainer

    @Composable
    override fun sectionHeaderContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun sectionHeaderTextStyle(): TextStyle = MaterialTheme.typography.titleMedium

    @Composable
    override fun sectionHeaderChevronContainerColor(): Color =
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)

    @Composable
    override fun sectionHeaderChevronContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun monthNavigationDescriptor(): String = stringResource(Res.string.expenses)
}
