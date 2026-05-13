package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardConcatenatedAmountsRow
import it.homebudget.app.database.DashboardDayAmountGroupRow
import it.homebudget.app.database.DashboardExpenseAggregates
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.MonthTotalRow
import it.homebudget.app.database.TopCategorySummaryRow
import kotlinx.datetime.TimeZone

private const val AMOUNT_GROUP_SEPARATOR = "|"

fun buildDashboardExpenseAggregates(
    expenseCount: Int,
    categoryAmountGroups: List<DashboardCategoryAmountGroupRow>,
    dayAmountGroups: List<DashboardDayAmountGroupRow>,
    sharedAmountGroup: DashboardConcatenatedAmountsRow,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): DashboardExpenseAggregates {
    val categoryTotals = categoryAmountGroups.toCategoryTotals()
    val totalAmount = categoryTotals.fold(ZERO) { acc, row -> acc + row.amount }
    val summary = ExpenseMonthSummaryRow(
        expenseCount = expenseCount,
        totalAmount = totalAmount,
        sharedAmount = sharedAmountGroup.toSummedAmount()
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
            amount = row.concatenatedAmounts.toSummedAmount()
        )
    }.sortedByDescending { row -> row.amount }
}

fun List<DashboardCategoryAmountGroupRow>.toTopCategorySummary(): TopCategorySummaryRow? {
    return map { row ->
        TopCategoryCandidate(
            categoryId = row.categoryId,
            amount = row.concatenatedAmounts.toSummedAmount(),
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
            amount = row.concatenatedAmounts.toSummedAmount()
        )
    }.maxByOrNull { row -> row.amount }
}

fun List<DashboardMonthAmountGroupRow>.toMonthTotals(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<MonthTotalRow> {
    return map { row ->
        MonthTotalRow(
            date = MonthKey(row.year, row.month).toStartOfMonthMillis(timeZone),
            amount = row.concatenatedAmounts.toSummedAmount()
        )
    }
}

fun DashboardConcatenatedAmountsRow.toSummedAmount(): BigInteger {
    return concatenatedAmounts.toSummedAmount()
}

fun String?.toSummedAmount(): BigInteger {
    if (this.isNullOrEmpty()) return ZERO

    var total = ZERO
    var startIndex = 0

    while (startIndex < length) {
        val separatorIndex = indexOf(AMOUNT_GROUP_SEPARATOR, startIndex)
        val endIndex = if (separatorIndex >= 0) separatorIndex else length
        total += substring(startIndex, endIndex).toAmountBigInteger()
        startIndex = endIndex + 1
    }

    return total
}

private data class TopCategoryCandidate(
    val categoryId: String,
    val amount: BigInteger,
    val latestExpenseDate: Long
)
