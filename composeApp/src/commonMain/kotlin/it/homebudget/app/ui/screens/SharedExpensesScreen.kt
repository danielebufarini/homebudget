package it.homebudget.app.ui.screens

import it.homebudget.app.database.Expense
import it.homebudget.app.localization.AppStrings

class SharedExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    override fun screenTitle(monthName: String): String = "$monthName ${AppStrings.sharedExpenses}"

    override fun emptyStateText(): String = AppStrings.noSharedExpensesForMonth()

    override fun expenseFallbackTitle(): String = AppStrings.sharedExpense

    override fun includeExpense(expense: Expense): Boolean = expense.isShared == 1L

    override fun centerAlignedTitle(): Boolean = true

    override fun monthNavigationDescriptor(): String = AppStrings.sharedExpenses
}
