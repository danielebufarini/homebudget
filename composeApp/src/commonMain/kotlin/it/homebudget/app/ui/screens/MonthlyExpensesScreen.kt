package it.homebudget.app.ui.screens

import it.homebudget.app.database.Expense

class MonthlyExpensesScreen(
    year: Int,
    month: Int
) : BaseGroupedExpensesScreen(year, month) {

    override fun screenTitle(monthName: String): String = "$monthName Expenses"

    override fun emptyStateText(): String = "No expenses for this month"

    override fun expenseFallbackTitle(): String = "Expense"

    override fun includeExpense(expense: Expense): Boolean = true
}
