package it.danielebufarini.homebudget.ui.screens.dashboard

import it.danielebufarini.homebudget.data.DashboardBalanceTrend
import it.danielebufarini.homebudget.data.DashboardMonthTotal
import it.danielebufarini.homebudget.ui.screens.MonthCursor
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardBalanceChartStateTest {

    @Test
    fun buildBalanceChartState_usesAllTimeCumulativeBalanceSeries() {
        val state = buildBalanceChartState(
            balanceTrend = DashboardBalanceTrend(
                initialExpenseAmount = 10_000L,
                initialIncomeAmount = 15_000L,
                expenseTotalsByMonth = listOf(
                    DashboardMonthTotal(year = 2026, month = 1, amount = 1_000L),
                    DashboardMonthTotal(year = 2026, month = 5, amount = 500L)
                ),
                incomeTotalsByMonth = listOf(
                    DashboardMonthTotal(year = 2026, month = 2, amount = 2_500L)
                )
            ),
            selectedMonth = MonthCursor(year = 2026, month = 5)
        )

        val balance = state.series.single()

        assertEquals(listOf(50.0, 40.0, 65.0, 65.0, 65.0, 60.0), balance.values)
        assertEquals(setOf(0, 1, 2, 3, 4, 5), balance.markerDays)

        val may = state.monthSnapshots.last()
        assertEquals(500L, may.expenseAmount)
        assertEquals(0L, may.incomeAmount)
        assertEquals(11_500L, may.cumulativeExpenseAmount)
        assertEquals(17_500L, may.cumulativeIncomeAmount)
        assertEquals(6_000L, may.cumulativeDifferenceAmount)
    }

    @Test
    fun buildBalanceChartState_keepsBaselineBalanceWhenVisibleMonthsAreEmpty() {
        val state = buildBalanceChartState(
            balanceTrend = DashboardBalanceTrend(
                initialExpenseAmount = 2_000L,
                initialIncomeAmount = 5_000L,
                expenseTotalsByMonth = emptyList(),
                incomeTotalsByMonth = emptyList()
            ),
            selectedMonth = MonthCursor(year = 2026, month = 5)
        )

        val balance = state.series.single()

        assertEquals(listOf(30.0, 30.0, 30.0, 30.0, 30.0, 30.0), balance.values)
        assertEquals(6, state.monthSnapshots.size)
        assertEquals(3_000L, state.monthSnapshots.last().cumulativeDifferenceAmount)
    }
}
