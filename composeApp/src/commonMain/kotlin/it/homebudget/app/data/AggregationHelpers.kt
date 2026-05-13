package it.homebudget.app.data

import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardDayAmountGroupRow
import it.homebudget.app.database.DashboardExpenseAggregates
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import it.homebudget.app.database.DashboardTotalAmountRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.MonthTotalRow
import it.homebudget.app.database.TopCategorySummaryRow
import kotlinx.datetime.TimeZone

fun buildDashboardExpenseAggregates(
    expenseCount: Int,
    categoryAmountGroups: List<DashboardCategoryAmountGroupRow>,
    dayAmountGroups: List<DashboardDayAmountGroupRow>,
    sharedAmountGroup: DashboardTotalAmountRow,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): DashboardExpenseAggregates {
    val categoryTotals = categoryAmountGroups.toCategoryTotals()
    val totalAmount = categoryTotals.fold(0L) { acc, row -> addAmountsExact(acc, row.amount) }
    val summary = ExpenseMonthSummaryRow(
        expenseCount = expenseCount,
        totalAmount = totalAmount,
        sharedAmount = sharedAmountGroup.totalAmount
    )

    return DashboardExpenseAggregates(
        summary = summary,
        categoryTotals = categoryTotals,
        topCategory = categoryAmountGroups.toTopCategorySummary(),
        highestDay = dayAmountGroups.toHighestDaySummary(timeZone)
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

fun List<DashboardDayAmountGroupRow>.toHighestDaySummary(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): HighestDaySummaryRow? {
    return map { row ->
        HighestDaySummaryRow(
            dayOfMonth = row.date.toDayOfMonth(timeZone),
            amount = row.totalAmount
        )
    }.maxByOrNull { row -> row.amount }
}

fun List<DashboardMonthAmountGroupRow>.toMonthTotals(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<MonthTotalRow> {
    return map { row ->
        MonthTotalRow(
            date = MonthKey(row.year, row.month).toStartOfMonthMillis(timeZone),
            amount = row.totalAmount
        )
    }
}

private data class TopCategoryCandidate(
    val categoryId: String,
    val amount: Long,
    val latestExpenseDate: Long
)
