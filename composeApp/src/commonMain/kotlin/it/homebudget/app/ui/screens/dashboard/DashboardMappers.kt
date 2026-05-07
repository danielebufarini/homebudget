package it.homebudget.app.ui.screens.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.data.DashboardCashFlow
import it.homebudget.app.data.DashboardCategoryTotal
import it.homebudget.app.data.DashboardMonthSummary
import it.homebudget.app.data.toDisplayDouble
import it.homebudget.app.ui.screens.MonthCursor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Clock

internal fun emptyDashboardMonthSummary() = DashboardMonthSummary(
    expenseCount = 0,
    totalAmount = ZERO,
    incomeAmount = ZERO,
    sharedAmount = ZERO,
    averageAmount = ZERO,
    topCategoryId = null,
    highestDayOfMonth = null,
    highestDayAmount = ZERO,
    categoryTotals = emptyList()
)

internal fun emptyLineChartState(selectedMonth: MonthCursor): LineChartState {
    val months = selectedMonth.trailingMonths(count = CASH_FLOW_CHART_MONTH_COUNT)

    return LineChartState(
        pointCount = months.size,
        minValue = 0.0,
        maxValue = 0.0,
        months = months,
        yAxisLabels = listOf("0", "0", "0"),
        monthSnapshots = emptyList(),
        series = emptyList()
    )
}

internal fun DashboardMonthSummary.toUiMonthlySummary(): MonthlySummary {
    val totalAmountDouble = totalAmount.toDisplayDouble().coerceAtLeast(0.01)

    return MonthlySummary(
        totalAmount = totalAmount,
        expenseCount = expenseCount,
        incomeAmount = incomeAmount,
        sharedAmount = sharedAmount,
        averageAmount = averageAmount,
        topCategoryId = topCategoryId,
        highestDayOfMonth = highestDayOfMonth,
        highestDayAmount = highestDayAmount,
        categoryTotals = categoryTotals.mapIndexed { index, categoryTotal ->
            categoryTotal.toUiCategoryTotal(
                totalAmount = totalAmountDouble,
                color = chartPalette[index % chartPalette.size]
            )
        }
    )
}

internal fun DashboardCategoryTotal.toUiCategoryTotal(
    totalAmount: Double,
    color: Color
): CategoryTotal {
    return CategoryTotal(
        categoryId = categoryId,
        amount = amount,
        fraction = (amount.toDisplayDouble() / totalAmount).coerceIn(0.0, 1.0),
        color = color
    )
}

internal fun buildCashFlowChartState(
    cashFlow: DashboardCashFlow,
    selectedMonth: MonthCursor
): LineChartState {
    val months = selectedMonth.trailingMonths(count = CASH_FLOW_CHART_MONTH_COUNT)
    val expenseTotalsByMonth = cashFlow.expenseTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }
    val incomeTotalsByMonth = cashFlow.incomeTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }

    if (expenseTotalsByMonth.isEmpty() && incomeTotalsByMonth.isEmpty()) {
        return emptyLineChartState(selectedMonth)
    }

    val monthSnapshots = months.map { month ->
        ChartMonthSnapshot(
            month = month,
            expenseAmount = expenseTotalsByMonth[month] ?: ZERO,
            incomeAmount = incomeTotalsByMonth[month] ?: ZERO
        )
    }
    val expenseValues = monthSnapshots.map { it.expenseAmount.toDisplayDouble() }
    val incomeValues = monthSnapshots.map { it.incomeAmount.toDisplayDouble() }
    val differenceValues = monthSnapshots.map { it.differenceAmount.toDisplayDouble() }

    val expenseMarkerDays = buildSet {
        months.forEachIndexed { index, month ->
            if (expenseTotalsByMonth[month] != null) add(index)
        }
    }
    val incomeMarkerDays = buildSet {
        months.forEachIndexed { index, month ->
            if (incomeTotalsByMonth[month] != null) add(index)
        }
    }
    val differenceMarkerDays = expenseMarkerDays + incomeMarkerDays

    val maxValue = maxOf(
        expenseValues.maxOrNull() ?: 0.0,
        incomeValues.maxOrNull() ?: 0.0,
        differenceValues.maxOrNull() ?: 0.0,
        1.0
    )
    val minValue = minOf(
        differenceValues.minOrNull() ?: 0.0,
        0.0
    )
    val middleValue = (maxValue + minValue) / 2.0

    return LineChartState(
        pointCount = months.size,
        minValue = minValue,
        maxValue = maxValue,
        months = months,
        yAxisLabels = listOf(
            formatAxisAmount(maxValue),
            formatAxisAmount(middleValue),
            formatAxisAmount(minValue)
        ),
        monthSnapshots = monthSnapshots,
        series = listOf(
            LineSeries(
                kind = ChartSeriesKind.Expenses,
                color = Color(0xFFC62828),
                values = expenseValues,
                markerDays = expenseMarkerDays
            ),
            LineSeries(
                kind = ChartSeriesKind.Income,
                color = Color(0xFF5BC98A),
                values = incomeValues,
                markerDays = incomeMarkerDays
            ),
            LineSeries(
                kind = ChartSeriesKind.Difference,
                color = Color(0xFF1565C0),
                values = differenceValues,
                markerDays = differenceMarkerDays
            )
        )
    )
}

internal fun formatAxisAmount(amount: Double): String = amount.roundToInt().toString()

internal fun MonthCursor.toDayLabel(dayOfMonth: Int, weekdayNames: List<String>): String {
    val dayOfWeek = kotlinx.datetime.LocalDate(year, month, dayOfMonth).dayOfWeek
    return "${weekdayNames[dayOfWeek.ordinal]} $dayOfMonth"
}

internal fun currentMonthCursor(): MonthCursor {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return MonthCursor(now.year, now.month.ordinal + 1)
}

internal fun LineChartState.buildChartGeometry(
    chartSize: IntSize,
    topInsetPx: Float
): LineChartGeometry? {
    if (chartSize.width <= 0 || chartSize.height <= 0 || pointCount <= 0 || series.isEmpty()) {
        return null
    }

    val plotHeight = (chartSize.height - topInsetPx).coerceAtLeast(1f)
    val normalizedMinValue = minValue
    val valueRange = (maxValue - normalizedMinValue).coerceAtLeast(1.0)

    fun xFor(index: Int): Float =
        if (pointCount == 1) chartSize.width / 2f
        else chartSize.width * index / (pointCount - 1).toFloat()

    fun yFor(value: Double): Float =
        topInsetPx + plotHeight -
                ((value - normalizedMinValue) / valueRange).toFloat() * plotHeight

    val horizontalGridYs = listOf(0f, 0.5f, 1f).map { marker ->
        topInsetPx + plotHeight * (1f - marker)
    }
    val verticalGridXs = List(pointCount, ::xFor)
    val zeroLineY = if (0.0 in normalizedMinValue..maxValue) {
        yFor(0.0)
    } else {
        null
    }

    val renderedSeries = series.map { series ->
        val points = series.values.mapIndexed { index, value ->
            Offset(x = xFor(index), y = yFor(value))
        }
        val path = Path().apply {
            points.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y)
                else lineTo(offset.x, offset.y)
            }
        }
        val markers = series.markerDays.mapNotNull { index ->
            points.getOrNull(index)?.let { point ->
                ChartPoint(
                    monthIndex = index,
                    kind = series.kind,
                    center = point
                )
            }
        }
        RenderedLineSeries(
            color = series.color,
            path = path,
            markers = markers
        )
    }
    val chartPoints = buildList {
        renderedSeries.forEach { addAll(it.markers) }
    }

    return LineChartGeometry(
        horizontalGridYs = horizontalGridYs,
        verticalGridXs = verticalGridXs,
        zeroLineY = zeroLineY,
        series = renderedSeries,
        points = chartPoints
    )
}

internal fun LineChartGeometry.findNearestPoint(
    tapOffset: Offset,
    hitTargetRadiusPx: Float
): ChartPoint? {
    var nearestPoint: ChartPoint? = null
    var nearestDistance = hitTargetRadiusPx

    points.forEach { point ->
        val distance = hypot(
            (tapOffset.x - point.center.x).toDouble(),
            (tapOffset.y - point.center.y).toDouble()
        ).toFloat()

        if (distance <= nearestDistance) {
            nearestDistance = distance
            nearestPoint = point
        }
    }

    return nearestPoint
}
