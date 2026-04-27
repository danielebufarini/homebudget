package it.homebudget.app.ui.screens

import it.homebudget.app.database.Expense

class CategoryExpensesScreen(
    year: Int,
    month: Int,
    private val categoryName: String
) : BaseGroupedExpensesScreen(year, month) {

    override fun screenTitle(monthName: String): String = "$monthName $categoryName"

    override fun emptyStateText(): String = "No expenses for $categoryName this month"

    override fun expenseFallbackTitle(): String = "Expense"

    override fun includeExpense(expense: Expense): Boolean = true

    override fun includeCategory(categoryName: String): Boolean = categoryName == this.categoryName

    override fun groupsExpandedByDefault(): Boolean = true
}
