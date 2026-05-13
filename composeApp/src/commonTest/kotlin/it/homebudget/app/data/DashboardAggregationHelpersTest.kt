package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.toBigInteger
import it.homebudget.app.database.DashboardCategoryAmountGroupRow
import it.homebudget.app.database.DashboardConcatenatedAmountsRow
import it.homebudget.app.database.DashboardDayAmountGroupRow
import it.homebudget.app.database.DashboardMonthAmountGroupRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardAggregationHelpersTest {

    @Test
    fun concatenatedAmounts_areSummedExactly() {
        assertEquals(0.toBigInteger(), null.toSummedAmount())
        assertEquals(0.toBigInteger(), "".toSummedAmount())
        assertEquals(600.toBigInteger(), "100|200|300".toSummedAmount())
    }

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
                    concatenatedAmounts = "200|100"
                ),
                DashboardCategoryAmountGroupRow(
                    categoryId = "rent",
                    latestExpenseDate = firstDay,
                    concatenatedAmounts = "400"
                )
            ),
            dayAmountGroups = listOf(
                DashboardDayAmountGroupRow(
                    date = firstDay,
                    concatenatedAmounts = "400"
                ),
                DashboardDayAmountGroupRow(
                    date = secondDay,
                    concatenatedAmounts = "200|100"
                )
            ),
            sharedAmountGroup = DashboardConcatenatedAmountsRow("100|400"),
            timeZone = TimeZone.UTC
        )

        assertEquals(3, aggregates.summary.expenseCount)
        assertEquals(700.toBigInteger(), aggregates.summary.totalAmount)
        assertEquals(500.toBigInteger(), aggregates.summary.sharedAmount)
        assertEquals("rent", aggregates.topCategory?.categoryId)
        assertEquals(1, aggregates.highestDay?.dayOfMonth)
        assertEquals(400.toBigInteger(), aggregates.highestDay?.amount)
    }

    @Test
    fun monthAmountGroups_areMappedToMonthTotals() {
        val totals = listOf(
            DashboardMonthAmountGroupRow(
                year = 2026,
                month = 4,
                concatenatedAmounts = "100|50"
            ),
            DashboardMonthAmountGroupRow(
                year = 2026,
                month = 5,
                concatenatedAmounts = "300"
            )
        ).toMonthTotals(timeZone = TimeZone.UTC)

        assertEquals(listOf(150.toBigInteger(), 300.toBigInteger()), totals.map { it.amount })
        assertEquals(
            listOf(
                MonthKey(2026, 4).toStartOfMonthMillis(TimeZone.UTC),
                MonthKey(2026, 5).toStartOfMonthMillis(TimeZone.UTC)
            ),
            totals.map { it.date }
        )
    }
}
