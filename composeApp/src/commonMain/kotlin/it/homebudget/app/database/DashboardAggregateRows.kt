package it.homebudget.app.database

// Raw Room projection: directly read from SQLite.
data class DashboardCategoryAmountGroupRow(
    val categoryId: String,
    val latestExpenseDate: Long,
    val totalAmount: Long
)

// Raw Room projection: directly read from SQLite.
data class DashboardDayAmountGroupRow(
    val date: Long,
    val totalAmount: Long
)

// Raw Room projection: directly read from SQLite.
data class DashboardMonthAmountGroupRow(
    val year: Int,
    val month: Int,
    val totalAmount: Long
)

// Raw Room projection: directly read from SQLite.
data class DashboardTotalAmountRow(
    val totalAmount: Long
)

// Raw Room projection: compact widget-only monthly totals.
data class WidgetMonthSummaryRow(
    val expenseAmount: Long,
    val incomeAmount: Long
)

// Computed in Kotlin.
data class ExpenseMonthSummaryRow(
    val expenseCount: Int,
    val totalAmount: Long,
    val sharedAmount: Long
)

// Computed in Kotlin.
data class DashboardExpenseAggregates(
    val summary: ExpenseMonthSummaryRow,
    val categoryTotals: List<CategoryTotalRow>,
    val topCategory: TopCategorySummaryRow?,
    val highestDay: HighestDaySummaryRow?
)

// Computed in Kotlin.
// date = first day of the month at start of day, in epoch millis.
data class MonthTotalRow(
    val date: Long,
    val amount: Long
)

// Computed in Kotlin.
data class CategoryTotalRow(
    val categoryId: String,
    val amount: Long
)

// Computed in Kotlin.
data class TopCategorySummaryRow(
    val categoryId: String,
    val amount: Long
)

// Computed in Kotlin.
data class HighestDaySummaryRow(
    val dayOfMonth: Int,
    val amount: Long
)
