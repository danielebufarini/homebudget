@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.database

import kotlin.native.ObjCName

// Raw Room projection: directly read from SQLite.
data class DashboardCategoryAmountGroupRow(
    val categoryId: String,
    val latestExpenseDate: Long,
    val totalAmount: Long
)

// Raw Room projection: compact monthly dashboard scalar totals.
data class DashboardMonthSummaryAmountRow(
    val expenseCount: Int,
    val totalAmount: Long,
    val sharedAmount: Long,
    val incomeAmount: Long
)

// Raw Room projection: directly read from SQLite.
data class DashboardMonthAmountGroupRow(
    val year: Int,
    val month: Int,
    val totalAmount: Long
)

// Raw Room projection: compact widget-only monthly totals.
data class WidgetMonthSummaryRow(
    val expenseAmount: Long,
    val incomeAmount: Long
)

// Raw Room projection: category usage counters for management UI.
data class CategoryUsageCountRow(
    val categoryId: String,
    val transactionCount: Long
)

// Raw Room projection: recent mixed expense/income dashboard rows.
data class DashboardRecentTransactionRow(
    val id: String,
    val typeOrdinal: Int,
    val amount: Long,
    val date: Long,
    val categoryId: String?,
    @property:ObjCName(swiftName = "transactionDescription")
    val description: String?
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
data class MonthTotalRow(
    val year: Int,
    val month: Int,
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
