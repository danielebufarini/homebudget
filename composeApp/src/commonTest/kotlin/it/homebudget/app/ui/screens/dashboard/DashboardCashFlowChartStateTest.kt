package it.homebudget.app.ui.screens.dashboard

import it.homebudget.app.data.DashboardCashFlow
import it.homebudget.app.data.DashboardMonthTotal
import it.homebudget.app.ui.screens.MonthCursor
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardCashFlowChartStateTest {

    @Test
    fun buildCashFlowChartState_usesCumulativeBalanceSeries() {
        val state = buildCashFlowChartState(
            cashFlow = DashboardCashFlow(
                expenseTotalsByMonth = listOf(
                    DashboardMonthTotal(year = 2026, month = 1, amount = 100L),
                    DashboardMonthTotal(year = 2026, month = 3, amount = 25L),
                    DashboardMonthTotal(year = 2026, month = 5, amount = 50L)
                ),
                incomeTotalsByMonth = listOf(
                    DashboardMonthTotal(year = 2026, month = 1, amount = 300L),
                    DashboardMonthTotal(year = 2026, month = 4, amount = 50L)
                )
            ),
            selectedMonth = MonthCursor(year = 2026, month = 5)
        )

        val balance = state.series.single { it.kind == ChartSeriesKind.Balance }

        assertEquals(listOf(0.0, 200.0, 200.0, 175.0, 225.0, 175.0), balance.values)
        assertEquals(setOf(0, 1, 2, 3, 4, 5), balance.markerDays)

        val may = state.monthSnapshots.last()
        assertEquals(50L, may.expenseAmount)
        assertEquals(0L, may.incomeAmount)
        assertEquals(175L, may.cumulativeExpenseAmount)
        assertEquals(350L, may.cumulativeIncomeAmount)
        assertEquals(175L, may.cumulativeDifferenceAmount)
    }
}
