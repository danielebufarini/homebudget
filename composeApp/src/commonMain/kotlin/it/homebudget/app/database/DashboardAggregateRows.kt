package it.homebudget.app.database

import com.ionspin.kotlin.bignum.integer.BigInteger

// Raw Room projection: directly read from SQLite.
data class DashboardCategoryAmountGroupRow(
    val categoryId: String,
    val latestExpenseDate: Long,
    val concatenatedAmounts: String
)

// Raw Room projection: directly read from SQLite.
data class DashboardDayAmountGroupRow(
    val date: Long,
    val concatenatedAmounts: String
)

// Raw Room projection: directly read from SQLite.
data class DashboardMonthAmountGroupRow(
    val year: Int,
    val month: Int,
    val concatenatedAmounts: String
)

// Raw Room projection: directly read from SQLite.
data class DashboardConcatenatedAmountsRow(
    val concatenatedAmounts: String?
)

// Computed in Kotlin.
data class ExpenseMonthSummaryRow(
    val expenseCount: Int,
    val totalAmount: BigInteger,
    val sharedAmount: BigInteger
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
    val amount: BigInteger
)

// Computed in Kotlin.
data class CategoryTotalRow(
    val categoryId: String,
    val amount: BigInteger
)

// Computed in Kotlin.
data class TopCategorySummaryRow(
    val categoryId: String,
    val amount: BigInteger
)

// Computed in Kotlin.
data class HighestDaySummaryRow(
    val dayOfMonth: Int,
    val amount: BigInteger
)
