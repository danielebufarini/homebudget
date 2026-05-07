package it.homebudget.app.ui.screens.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.ui.screens.MonthCursor

internal data class MonthlySummary(
    val totalAmount: BigInteger,
    val expenseCount: Int,
    val incomeAmount: BigInteger,
    val sharedAmount: BigInteger,
    val averageAmount: BigInteger,
    val topCategoryId: String?,
    val highestDayOfMonth: Int?,
    val highestDayAmount: BigInteger,
    val categoryTotals: List<CategoryTotal>
)

internal data class CategoryTotal(
    val categoryId: String?,
    val amount: BigInteger,
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

internal data class LineChartState(
    val pointCount: Int,
    val minValue: Double,
    val maxValue: Double,
    val months: List<MonthCursor>,
    val yAxisLabels: List<String>,
    val monthSnapshots: List<ChartMonthSnapshot>,
    val series: List<LineSeries>
)

internal enum class ChartSeriesKind {
    Expenses,
    Income,
    Difference
}

internal data class LineSeries(
    val kind: ChartSeriesKind,
    val color: Color,
    val values: List<Double>,
    val markerDays: Set<Int> = emptySet()
)

internal data class ChartMonthSnapshot(
    val month: MonthCursor,
    val expenseAmount: BigInteger,
    val incomeAmount: BigInteger
) {
    val differenceAmount: BigInteger
        get() = incomeAmount - expenseAmount
}

internal data class ChartPoint(
    val monthIndex: Int,
    val kind: ChartSeriesKind,
    val center: Offset
)

internal data class SelectedChartPoint(
    val monthIndex: Int,
    val detail: ChartMonthSnapshot,
    val anchor: Offset
)

internal data class RenderedLineSeries(
    val color: Color,
    val path: Path,
    val markers: List<ChartPoint>
)

internal data class LineChartGeometry(
    val horizontalGridYs: List<Float>,
    val verticalGridXs: List<Float>,
    val zeroLineY: Float?,
    val series: List<RenderedLineSeries>,
    val points: List<ChartPoint>
)
