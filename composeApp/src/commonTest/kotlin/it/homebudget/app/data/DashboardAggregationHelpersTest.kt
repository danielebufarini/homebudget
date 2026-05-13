package it.homebudget.app.data

import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardDayAmountGroupRow
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import it.homebudget.app.database.DashboardTotalAmountRow
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
            expenseCount = 3,
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
            dayAmountGroups = listOf(
                DashboardDayAmountGroupRow(
                    date = firstDay,
                    totalAmount = 400L
                ),
                DashboardDayAmountGroupRow(
                    date = secondDay,
                    totalAmount = 300L
                )
            ),
            sharedAmountGroup = DashboardTotalAmountRow(500L),
            timeZone = TimeZone.UTC
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
        ).toMonthTotals(timeZone = TimeZone.UTC)

        assertEquals(listOf(150L, 300L), totals.map { it.amount })
        assertEquals(
            listOf(
                MonthKey(2026, 4).toStartOfMonthMillis(TimeZone.UTC),
                MonthKey(2026, 5).toStartOfMonthMillis(TimeZone.UTC)
            ),
            totals.map { it.date }
        )
    }
}
