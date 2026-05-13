package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.DashboardExpenseAggregates
import it.homebudget.app.database.DashboardExpenseRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.TopCategorySummaryRow
import kotlinx.datetime.TimeZone

fun List<DashboardExpenseRow>.toDashboardExpenseAggregates(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): DashboardExpenseAggregates {
    var totalAmount = ZERO
    var sharedAmount = ZERO
    val categoryTotals = linkedMapOf<String, BigInteger>()
    val latestExpenseDatesByCategory = linkedMapOf<String, Long>()
    val dayTotals = linkedMapOf<Int, BigInteger>()

    forEach { row ->
        val amount = row.amount.toAmountBigInteger()
        totalAmount += amount
        if (row.isShared) {
            sharedAmount += amount
        }

        categoryTotals[row.categoryId] = (categoryTotals[row.categoryId] ?: ZERO) + amount
        latestExpenseDatesByCategory[row.categoryId] =
            maxOf(latestExpenseDatesByCategory[row.categoryId] ?: Long.MIN_VALUE, row.date)

        val dayOfMonth = row.date.toDayOfMonth(timeZone)
        dayTotals[dayOfMonth] = (dayTotals[dayOfMonth] ?: ZERO) + amount
    }

    val summary = ExpenseMonthSummaryRow(
        expenseCount = size,
        totalAmount = totalAmount,
        sharedAmount = sharedAmount
    )
    val totalsByCategory = categoryTotals.map { (categoryId, amount) ->
        CategoryTotalRow(
            categoryId = categoryId,
            amount = amount
        )
    }.sortedByDescending { row -> row.amount }
    val topCategory = categoryTotals
        .map { (categoryId, amount) ->
            TopCategoryCandidate(
                categoryId = categoryId,
                amount = amount,
                latestExpenseDate = latestExpenseDatesByCategory.getValue(categoryId)
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
    val highestDay = dayTotals
        .map { (dayOfMonth, amount) ->
            HighestDaySummaryRow(
                dayOfMonth = dayOfMonth,
                amount = amount
            )
        }
        .maxByOrNull { row -> row.amount }

    return DashboardExpenseAggregates(
        summary = summary,
        categoryTotals = totalsByCategory,
        topCategory = topCategory,
        highestDay = highestDay
    )
}

fun List<DashboardExpenseRow>.toExpenseMonthSummary(): ExpenseMonthSummaryRow {
    return toDashboardExpenseAggregates().summary
}

fun List<DashboardExpenseRow>.toCategoryTotals(): List<CategoryTotalRow> {
    return toDashboardExpenseAggregates().categoryTotals
}

fun List<DashboardExpenseRow>.toTopCategorySummary(): TopCategorySummaryRow? {
    return toDashboardExpenseAggregates().topCategory
}

fun List<DashboardExpenseRow>.toHighestDaySummary(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): HighestDaySummaryRow? {
    return toDashboardExpenseAggregates(timeZone).highestDay
}

private data class TopCategoryCandidate(
    val categoryId: String,
    val amount: BigInteger,
    val latestExpenseDate: Long
)
