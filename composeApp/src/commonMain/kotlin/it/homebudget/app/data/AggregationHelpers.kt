package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.DashboardExpenseRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.TopCategorySummaryRow
import kotlinx.datetime.TimeZone

fun List<DashboardExpenseRow>.toExpenseMonthSummary(): ExpenseMonthSummaryRow {
    val totalAmount = fold(ZERO) { acc, row ->
        acc + row.amount.toAmountBigInteger()
    }

    val sharedAmount = fold(ZERO) { acc, row ->
        if (row.isShared) {
            acc + row.amount.toAmountBigInteger()
        } else {
            acc
        }
    }

    return ExpenseMonthSummaryRow(
        expenseCount = size,
        totalAmount = totalAmount,
        sharedAmount = sharedAmount
    )
}

fun List<DashboardExpenseRow>.toCategoryTotals(): List<CategoryTotalRow> {
    return groupBy { row -> row.categoryId }
        .map { (categoryId, rows) ->
            CategoryTotalRow(
                categoryId = categoryId,
                amount = rows.fold(ZERO) { acc, row ->
                    acc + row.amount.toAmountBigInteger()
                }
            )
        }
        .sortedByDescending { row -> row.amount }
}

fun List<DashboardExpenseRow>.toTopCategorySummary(): TopCategorySummaryRow? {
    return groupBy { row -> row.categoryId }
        .map { (categoryId, rows) ->
            val amount = rows.fold(ZERO) { acc, row ->
                acc + row.amount.toAmountBigInteger()
            }

            val latestExpenseDate = rows.maxOf { row -> row.date }

            TopCategoryCandidate(
                categoryId = categoryId,
                amount = amount,
                latestExpenseDate = latestExpenseDate
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

private data class TopCategoryCandidate(
    val categoryId: String,
    val amount: BigInteger,
    val latestExpenseDate: Long
)

fun List<DashboardExpenseRow>.toHighestDaySummary(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): HighestDaySummaryRow? {
    return groupBy { row -> row.date.toDayOfMonth(timeZone) }
        .map { (dayOfMonth, rows) ->
            HighestDaySummaryRow(
                dayOfMonth = dayOfMonth,
                amount = rows.fold(ZERO) { acc, row ->
                    acc + row.amount.toAmountBigInteger()
                }
            )
        }
        .maxByOrNull { row -> row.amount }
}