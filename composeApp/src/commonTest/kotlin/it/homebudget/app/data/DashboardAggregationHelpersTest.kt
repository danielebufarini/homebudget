package it.homebudget.app.data

import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardAggregationHelpersTest {

    @Test
    fun dashboardExpenseAggregates_useBucketedRows() {
        val firstDay = LocalDate(2026, 5, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val secondDay = LocalDate(2026, 5, 2).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        val aggregates = buildDashboardExpenseAggregates(
            summary = ExpenseMonthSummaryRow(
                expenseCount = 3,
                totalAmount = 700L,
                sharedAmount = 500L
            ),
            categoryAmountGroups = listOf(
                DashboardCategoryAmountGroupRow(
                    categoryId = "food",
                    latestExpenseDate = secondDay,
                    totalAmount = 300L
                ),
                DashboardCategoryAmountGroupRow(
                    categoryId = "rent",
                    latestExpenseDate = firstDay,
                    totalAmount = 400L
                )
            ),
            highestDay = HighestDaySummaryRow(
                dayOfMonth = 1,
                amount = 400L
            )
        )

        assertEquals(3, aggregates.summary.expenseCount)
        assertEquals(700L, aggregates.summary.totalAmount)
        assertEquals(500L, aggregates.summary.sharedAmount)
        assertEquals("rent", aggregates.topCategory?.categoryId)
        assertEquals(1, aggregates.highestDay?.dayOfMonth)
        assertEquals(400L, aggregates.highestDay?.amount)
    }

    @Test
    fun monthAmountGroups_areMappedToMonthTotals() {
        val totals = listOf(
            DashboardMonthAmountGroupRow(
                year = 2026,
                month = 4,
                totalAmount = 150L
            ),
            DashboardMonthAmountGroupRow(
                year = 2026,
                month = 5,
                totalAmount = 300L
            )
        ).toMonthTotals()

        assertEquals(listOf(150L, 300L), totals.map { it.amount })
        assertEquals(listOf(2026, 2026), totals.map { it.year })
        assertEquals(listOf(4, 5), totals.map { it.month })
    }
}
