package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import it.danielebufarini.spesify.database.Expense
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.no_shared_expenses_for_month
import spesify.composeapp.generated.resources.shared_expense
import spesify.composeapp.generated.resources.shared_expenses

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
    override fun monthNavigationDescriptor(): String = stringResource(Res.string.shared_expenses)
}
