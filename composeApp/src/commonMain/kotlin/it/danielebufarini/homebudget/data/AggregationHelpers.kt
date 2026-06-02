package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.CategoryTotalRow
import it.danielebufarini.homebudget.database.DashboardCategoryAmountGroupRow
import it.danielebufarini.homebudget.database.DashboardExpenseAggregates
import it.danielebufarini.homebudget.database.DashboardMonthAmountGroupRow
import it.danielebufarini.homebudget.database.ExpenseMonthSummaryRow
import it.danielebufarini.homebudget.database.HighestDaySummaryRow
import it.danielebufarini.homebudget.database.MonthTotalRow
import it.danielebufarini.homebudget.database.TopCategorySummaryRow

fun buildDashboardExpenseAggregates(
    summary: ExpenseMonthSummaryRow,
    categoryAmountGroups: List<DashboardCategoryAmountGroupRow>,
    highestDay: HighestDaySummaryRow?
): DashboardExpenseAggregates {
    val categoryTotals = categoryAmountGroups.toCategoryTotals()

    return DashboardExpenseAggregates(
        summary = summary,
        categoryTotals = categoryTotals,
        topCategory = categoryAmountGroups.toTopCategorySummary(),
        highestDay = highestDay
    )
}

fun List<DashboardCategoryAmountGroupRow>.toCategoryTotals(): List<CategoryTotalRow> {
    return map { row ->
        CategoryTotalRow(
            categoryId = row.categoryId,
            amount = row.totalAmount
        )
    }.sortedByDescending { row -> row.amount }
}

fun List<DashboardCategoryAmountGroupRow>.toTopCategorySummary(): TopCategorySummaryRow? {
    return map { row ->
        TopCategoryCandidate(
            categoryId = row.categoryId,
            amount = row.totalAmount,
            latestExpenseDate = row.latestExpenseDate
        )
    }
        .sortedWith(
            compareByDescending<TopCategoryCandidate> { it.amount }
                .thenByDescending { it.latestExpenseDate }
        )
        .firstOrNull()
        ?.let { candidate ->
            TopCategorySummaryRow(
                categoryId = candidate.categoryId,
                amount = candidate.amount
            )
        }
}

fun List<DashboardMonthAmountGroupRow>.toMonthTotals(): List<MonthTotalRow> {
    return map { row ->
        MonthTotalRow(
            year = row.year,
            month = row.month,
            amount = row.totalAmount
        )
    }
}

private data class TopCategoryCandidate(
    val categoryId: String,
    val amount: Long,
    val latestExpenseDate: Long
)
