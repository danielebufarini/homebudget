package it.homebudget.app.data

data class DashboardMonthTotal(
    val year: Int,
    val month: Int,
    val amount: Long
)

data class DashboardCategoryTotal(
    val categoryId: String,
    val amount: Long
)

data class DashboardMonthSummary(
    val expenseCount: Int,
    val totalAmount: Long,
    val incomeAmount: Long,
    val sharedAmount: Long,
    val averageAmount: Long,
    val topCategoryId: String?,
    val highestDayOfMonth: Int?,
    val highestDayAmount: Long,
    val categoryTotals: List<DashboardCategoryTotal>
)

data class DashboardCashFlow(
    val expenseTotalsByMonth: List<DashboardMonthTotal>,
    val incomeTotalsByMonth: List<DashboardMonthTotal>
)

enum class DashboardRecentTransactionType {
    Expense,
    Income
}

data class DashboardRecentTransaction(
    val id: String,
    val type: DashboardRecentTransactionType,
    val amount: Long,
    val date: Long,
    val categoryId: String?,
    val description: String?
)

data class WidgetMonthSummary(
    val expenseAmount: Long,
    val incomeAmount: Long
)
