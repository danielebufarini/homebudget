package it.homebudget.app.data

import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardExpenseAggregates
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.MonthTotalRow
import it.homebudget.app.database.TopCategorySummaryRow

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
