package it.homebudget.app.ui.screens.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import it.homebudget.app.data.DashboardBalanceTrend
import it.homebudget.app.data.DashboardCategoryTotal
import it.homebudget.app.data.DashboardMonthSummary
import it.homebudget.app.data.addAmountsExact
import it.homebudget.app.data.toDisplayDouble
import it.homebudget.app.ui.screens.MonthCursor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Clock

internal fun emptyDashboardMonthSummary() = DashboardMonthSummary(
    expenseCount = 0,
    totalAmount = 0L,
    incomeAmount = 0L,
    sharedAmount = 0L,
    averageAmount = 0L,
    topCategoryId = null,
    highestDayOfMonth = null,
    highestDayAmount = 0L,
    categoryTotals = emptyList()
)

internal fun emptyBalanceChartState(selectedMonth: MonthCursor): BalanceChartState {
    val months = selectedMonth.trailingMonths(count = BALANCE_CHART_MONTH_COUNT)

    return BalanceChartState(
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

internal fun buildBalanceChartState(
    balanceTrend: DashboardBalanceTrend,
    selectedMonth: MonthCursor
): BalanceChartState {
    val months = selectedMonth.trailingMonths(count = BALANCE_CHART_MONTH_COUNT)
    val expenseTotalsByMonth = balanceTrend.expenseTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }
    val incomeTotalsByMonth = balanceTrend.incomeTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }

    if (
        expenseTotalsByMonth.isEmpty() &&
        incomeTotalsByMonth.isEmpty() &&
        balanceTrend.initialExpenseAmount == 0L &&
        balanceTrend.initialIncomeAmount == 0L
    ) {
        return emptyBalanceChartState(selectedMonth)
    }

    var cumulativeExpenseAmount = balanceTrend.initialExpenseAmount
    var cumulativeIncomeAmount = balanceTrend.initialIncomeAmount
    val monthSnapshots = months.map { month ->
        val expenseAmount = expenseTotalsByMonth[month] ?: 0L
        val incomeAmount = incomeTotalsByMonth[month] ?: 0L

        cumulativeExpenseAmount = addAmountsExact(cumulativeExpenseAmount, expenseAmount)
        cumulativeIncomeAmount = addAmountsExact(cumulativeIncomeAmount, incomeAmount)

        BalanceMonthSnapshot(
            month = month,
            expenseAmount = expenseAmount,
            incomeAmount = incomeAmount,
            cumulativeExpenseAmount = cumulativeExpenseAmount,
            cumulativeIncomeAmount = cumulativeIncomeAmount
        )
    }
    val balanceValues = monthSnapshots.map { it.cumulativeDifferenceAmount.toDisplayDouble() }
    val balanceMarkerDays = months.indices.toSet()

    val rawMaxValue = balanceValues.maxOrNull() ?: 0.0
    val rawMinValue = balanceValues.minOrNull() ?: 0.0
    val maxValue = if (rawMaxValue == 0.0 && rawMinValue == 0.0) {
        1.0
    } else {
        maxOf(rawMaxValue, 0.0)
    }
    val minValue = minOf(rawMinValue, 0.0)
    val middleValue = (maxValue + minValue) / 2.0

    return BalanceChartState(
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
            BalanceChartSeries(
                color = Color(0xFF1565C0),
                values = balanceValues,
                markerDays = balanceMarkerDays
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

internal fun BalanceChartState.buildBalanceChartGeometry(
    chartSize: IntSize,
    topInsetPx: Float
): BalanceChartGeometry? {
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
        val baselineY = zeroLineY ?: yFor(normalizedMinValue)
        val fillPath = if (points.isEmpty()) {
            null
        } else {
            Path().apply {
                points.forEachIndexed { index, offset ->
                    if (index == 0) moveTo(offset.x, offset.y)
                    else lineTo(offset.x, offset.y)
                }
                lineTo(points.last().x, baselineY)
                lineTo(points.first().x, baselineY)
                close()
            }
        }
        val markers = series.markerDays.mapNotNull { index ->
            points.getOrNull(index)?.let { point ->
                BalanceChartPoint(
                    monthIndex = index,
                    center = point
                )
            }
        }
        RenderedBalanceChartSeries(
            color = series.color,
            path = path,
            fillPath = fillPath,
            markers = markers
        )
    }
    val chartPoints = buildList {
        renderedSeries.forEach { addAll(it.markers) }
    }

    return BalanceChartGeometry(
        horizontalGridYs = horizontalGridYs,
        verticalGridXs = verticalGridXs,
        zeroLineY = zeroLineY,
        series = renderedSeries,
        points = chartPoints
    )
}

internal fun BalanceChartGeometry.findNearestPoint(
    tapOffset: Offset,
    hitTargetRadiusPx: Float
): BalanceChartPoint? {
    var nearestPoint: BalanceChartPoint? = null
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
