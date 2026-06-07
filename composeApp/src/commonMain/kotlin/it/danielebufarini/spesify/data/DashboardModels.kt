@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.data

import kotlin.native.ObjCName

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

data class DashboardBalanceTrend(
    val initialExpenseAmount: Long = 0L,
    val initialIncomeAmount: Long = 0L,
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
    @property:ObjCName(swiftName = "transactionDescription")
    val description: String?
)

data class WidgetMonthSummary(
    val expenseAmount: Long,
    val incomeAmount: Long
)
