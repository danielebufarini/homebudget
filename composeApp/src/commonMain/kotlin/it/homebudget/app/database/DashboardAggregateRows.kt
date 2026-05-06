package it.homebudget.app.database

data class ExpenseMonthSummaryRow(
    val expenseCount: Int,
    val totalAmount: Long,
    val sharedAmount: Long
)

data class MonthTotalRow(
    val year: Int,
    val month: Int,
    val amount: Long
)

data class CategoryTotalRow(
    val categoryId: String,
    val amount: Long
)

data class TopCategorySummaryRow(
    val categoryId: String,
    val amount: Long
)

data class HighestDaySummaryRow(
    val dayOfMonth: Int,
    val amount: Long
)
