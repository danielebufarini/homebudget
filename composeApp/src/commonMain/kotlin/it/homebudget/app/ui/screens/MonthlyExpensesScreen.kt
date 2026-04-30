package it.homebudget.app.ui.screens

import it.homebudget.app.database.Expense
import it.homebudget.app.localization.AppStrings

class MonthlyExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    override fun screenTitle(monthName: String): String = "$monthName ${AppStrings.expenses}"

    override fun emptyStateText(): String = AppStrings.noExpensesForMonth

    override fun expenseFallbackTitle(): String = AppStrings.expense

    override fun includeExpense(expense: Expense): Boolean = true

    override fun canAddExpense(): Boolean = true

    override fun monthNavigationDescriptor(): String = AppStrings.expenses
}
