package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import it.danielebufarini.spesify.data.subtractAmountsExact
import it.danielebufarini.spesify.ui.screens.common.MonthCursor

internal data class MonthlySummary(
    val totalAmount: Long,
    val expenseCount: Int,
    val incomeAmount: Long,
    val sharedAmount: Long,
    val averageAmount: Long,
    val topCategoryId: String?,
    val highestDayOfMonth: Int?,
    val highestDayAmount: Long,
    val categoryTotals: List<CategoryTotal>
)

internal data class CategoryTotal(
    val categoryId: String?,
    val amount: Long,
    val fraction: Double,
    val color: Color
)

internal data class SummaryMetricUi(
    val label: String,
    val value: String,
    val valueIconColorKey: String? = null,
    val valueIconKey: String? = null,
    val containerColor: Color,
    val contentColor: Color,
    val trailingValue: String? = null,
    val onClick: (() -> Unit)? = null
)

internal data class BalanceChartState(
    val pointCount: Int,
    val minValue: Double,
    val maxValue: Double,
    val months: List<MonthCursor>,
    val yAxisLabels: List<String>,
    val monthSnapshots: List<BalanceMonthSnapshot>,
    val series: List<BalanceChartSeries>
)

internal data class BalanceChartSeries(
    val color: Color,
    val values: List<Double>,
    val markerDays: Set<Int> = emptySet()
)

internal data class BalanceMonthSnapshot(
    val month: MonthCursor,
    val expenseAmount: Long,
    val incomeAmount: Long,
    val cumulativeExpenseAmount: Long,
    val cumulativeIncomeAmount: Long
) {
    val differenceAmount: Long
        get() = subtractAmountsExact(incomeAmount, expenseAmount)

    val cumulativeDifferenceAmount: Long
        get() = subtractAmountsExact(cumulativeIncomeAmount, cumulativeExpenseAmount)
}

internal data class BalanceChartPoint(
    val monthIndex: Int,
    val center: Offset
)

internal data class SelectedBalanceChartPoint(
    val monthIndex: Int,
    val detail: BalanceMonthSnapshot,
    val anchor: Offset
)

internal data class RenderedBalanceChartSeries(
    val color: Color,
    val path: Path,
    val fillPath: Path?,
    val markers: List<BalanceChartPoint>
)

internal data class BalanceChartGeometry(
    val horizontalGridYs: List<Float>,
    val verticalGridXs: List<Float>,
    val zeroLineY: Float?,
    val series: List<RenderedBalanceChartSeries>,
    val points: List<BalanceChartPoint>
)
