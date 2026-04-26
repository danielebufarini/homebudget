package it.homebudget.app.ui.screens

import it.homebudget.app.database.Expense

class SharedExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    override fun screenTitle(monthName: String): String = "${monthName} Shared Expenses"

    override fun emptyStateText(): String = "No shared expenses for this month"

    override fun categoryCountLabel(count: Int): String = "$count shared expenses"

    override fun expenseFallbackTitle(): String = "Shared expense"

    override fun includeExpense(expense: Expense): Boolean = expense.isShared == 1L

    override fun centerAlignedTitle(): Boolean = true
}
