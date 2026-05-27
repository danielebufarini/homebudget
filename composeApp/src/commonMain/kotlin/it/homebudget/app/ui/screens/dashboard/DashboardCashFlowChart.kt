package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.formatAmount
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.shortLabel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun BalanceChartPage(
    strings: DashboardStrings,
    state: BalanceChartState
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val xAxisLabelBandHeight = 28.dp
    val xAxisLabels = state.months
    val density = LocalDensity.current
    var selectedPoint by remember(state) { mutableStateOf<SelectedBalanceChartPoint?>(null) }
    var rootPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var chartPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    val topInsetPx = with(density) { 8.dp.toPx() }
    val zeroAxisLabelHalfHeightPx = with(density) { 8.dp.toPx() }
    val hitTargetRadiusPx = with(density) { 18.dp.toPx() }
    val chartGeometry = remember(state, chartSize, topInsetPx) {
        state.buildBalanceChartGeometry(chartSize = chartSize, topInsetPx = topInsetPx)
    }
    val shouldShowZeroAxisLabel = remember(chartGeometry) {
        val geometry = chartGeometry ?: return@remember false
        val zeroY = geometry.zeroLineY ?: return@remember false

        geometry.horizontalGridYs.none { gridY ->
            kotlin.math.abs(gridY - zeroY) < 0.5f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { coordinates ->
                rootPositionInRoot = coordinates.positionInRoot()
                rootSize = coordinates.size
            }
            .pointerInput(chartGeometry, chartPositionInRoot, rootPositionInRoot, chartSize, hitTargetRadiusPx) {
                detectTapGestures { tapOffset ->
                    val chartOrigin = chartPositionInRoot - rootPositionInRoot
                    val tapInChart = tapOffset - chartOrigin
                    val insideChart = tapInChart.x in 0f..chartSize.width.toFloat() &&
                            tapInChart.y in 0f..chartSize.height.toFloat()

                    val nearestPoint = if (insideChart) {
                        chartGeometry?.findNearestPoint(
                            tapOffset = tapInChart,
                            hitTargetRadiusPx = hitTargetRadiusPx
                        )
                    } else {
                        null
                    }

                    selectedPoint = nearestPoint?.let { point ->
                        SelectedBalanceChartPoint(
                            monthIndex = point.monthIndex,
                            detail = state.monthSnapshots[point.monthIndex],
                            anchor = point.center + chartOrigin
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.series.isEmpty()) {
                Text(
                    text = strings.noExpensesInPeriod,
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).width(36.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            state.yAxisLabels.forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariant
                                )
                            }
                        }

                        if (shouldShowZeroAxisLabel) {
                            val zeroY = chartGeometry?.zeroLineY ?: return@Box
                            val maxOffsetY = (chartSize.height - zeroAxisLabelHalfHeightPx * 2)
                                .coerceAtLeast(0f)
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset {
                                        IntOffset(
                                            x = 0,
                                            y = (zeroY - zeroAxisLabelHalfHeightPx)
                                                .coerceIn(0f, maxOffsetY)
                                                .roundToInt()
                                        )
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(xAxisLabelBandHeight))
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                chartPositionInRoot = coordinates.positionInRoot()
                                chartSize = coordinates.size
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineWidth = 2.75.dp.toPx()
                            val gridStroke = 1.dp.toPx()
                            val geometry = chartGeometry ?: return@Canvas

                            geometry.horizontalGridYs.forEach { y ->
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = gridStroke
                                )
                            }

                            geometry.zeroLineY?.let { zeroY ->
                                drawLine(
                                    color = onSurfaceVariant.copy(alpha = 0.72f),
                                    start = Offset(0f, zeroY),
                                    end = Offset(size.width, zeroY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            geometry.verticalGridXs.forEach { x ->
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = gridStroke
                                )
                            }

                            geometry.series.forEach { series ->
                                series.fillPath?.let { fillPath ->
                                    drawPath(
                                        path = fillPath,
                                        color = series.color.copy(alpha = 0.16f)
                                    )
                                }

                                drawPath(
                                    path = series.path,
                                    color = series.color,
                                    style = Stroke(width = lineWidth, cap = StrokeCap.Round)
                                )

                                series.markers.forEach { marker ->
                                    drawCircle(
                                        color = series.color,
                                        radius = if (selectedPoint?.monthIndex == marker.monthIndex) {
                                            6.5.dp.toPx()
                                        } else {
                                            5.75.dp.toPx()
                                        },
                                        center = marker.center
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(xAxisLabelBandHeight),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        xAxisLabels.forEach { month ->
                            Text(
                                text = month.shortLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

        }

        selectedPoint?.let { popupPoint ->
            BalancePointPopup(
                strings = strings,
                point = popupPoint,
                rootSize = rootSize,
                popupSize = popupSize,
                onPopupSizeChanged = { popupSize = it }
            )
        }
    }
}

@Composable
private fun BoxScope.BalancePointPopup(
    strings: DashboardStrings,
    point: SelectedBalanceChartPoint,
    rootSize: IntSize,
    popupSize: IntSize,
    onPopupSizeChanged: (IntSize) -> Unit
) {
    val xMarginPx = with(LocalDensity.current) { 12.dp.toPx() }
    val yMarginPx = with(LocalDensity.current) { 8.dp.toPx() }
    val preferredX = point.anchor.x + xMarginPx
    val preferredY = point.anchor.y - popupSize.height / 2f
    val clampedX = if (rootSize.width == 0) {
        preferredX
    } else {
        min(
            max(8f, preferredX),
            max(8f, rootSize.width - popupSize.width - 8f)
        )
    }
    val clampedY = if (rootSize.height == 0) {
        preferredY
    } else {
        min(
            max(8f, preferredY),
            max(8f, rootSize.height - popupSize.height - yMarginPx)
        )
    }

    Card(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            .onGloballyPositioned { coordinates -> onPopupSizeChanged(coordinates.size) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = point.detail.month.shortLabelWithFullYear(strings.shortMonthNames),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BalancePointPopupRow(
                label = strings.cumulativeBalance,
                value = formatAmount(point.detail.cumulativeDifferenceAmount, strings.currencySymbol)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.thisMonth,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BalancePointPopupRow(
                label = strings.income,
                value = formatAmount(point.detail.incomeAmount, strings.currencySymbol)
            )
            BalancePointPopupRow(
                label = strings.expenses,
                value = formatAmount(point.detail.expenseAmount, strings.currencySymbol)
            )
            BalancePointPopupRow(
                label = strings.difference,
                value = formatAmount(point.detail.differenceAmount, strings.currencySymbol)
            )
        }
    }
}

private fun MonthCursor.shortLabelWithFullYear(shortMonthNames: List<String>): String =
    "${shortMonthNames[month - 1]} $year"

@Composable
private fun BalancePointPopupRow(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
