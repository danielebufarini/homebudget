package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun InstallmentRulerPicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntRange = 1..30,
    singlePaymentLabel: String,
    installmentsLabel: String,
    icon: ImageVector,
) {
    val minValue = valueRange.first
    val maxValue = valueRange.last
    val coercedValue = value.coerceIn(minValue, maxValue)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tickSpacingPx = with(density) { 18.dp.toPx() }
    val rulerPosition = remember { Animatable(coercedValue.toFloat()) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastAnnouncedValue by remember { mutableStateOf(coercedValue) }

    LaunchedEffect(coercedValue) {
        lastAnnouncedValue = coercedValue
        if (!isDragging && abs(rulerPosition.value - coercedValue.toFloat()) > 0.01f) {
            rulerPosition.animateTo(
                targetValue = coercedValue.toFloat(),
                animationSpec = spring(stiffness = 520f, dampingRatio = 0.82f),
            )
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (!enabled) return@rememberDraggableState
        val nextPosition = (rulerPosition.value - delta / tickSpacingPx).coerceIn(
            minimumValue = minValue.toFloat(),
            maximumValue = maxValue.toFloat(),
        )
        val nextSnappedValue = nextPosition.roundToInt().coerceIn(minValue, maxValue)
        dragJob?.cancel()
        dragJob = scope.launch {
            rulerPosition.snapTo(nextPosition)
        }
        if (nextSnappedValue != lastAnnouncedValue) {
            lastAnnouncedValue = nextSnappedValue
            currentOnValueChange(nextSnappedValue)
        }
    }

    val displayedValue = rulerPosition.value.roundToInt().coerceIn(minValue, maxValue)
    val valueCaption = if (displayedValue == 1) {
        singlePaymentLabel
    } else {
        installmentsLabel.lowercase()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .alpha(if (enabled) 1f else 0.56f)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InstallmentRulerCanvas(
                position = rulerPosition.value,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f))
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        enabled = enabled,
                        onDragStarted = {
                            dragJob?.cancel()
                            isDragging = true
                            lastAnnouncedValue = rulerPosition.value.roundToInt().coerceIn(minValue, maxValue)
                        },
                        onDragStopped = { velocity ->
                            val projectedPosition = (
                                rulerPosition.value - velocity * 0.14f / tickSpacingPx
                                ).coerceIn(
                                minimumValue = minValue.toFloat(),
                                maximumValue = maxValue.toFloat(),
                            )
                            val targetValue = projectedPosition.roundToInt().coerceIn(minValue, maxValue)
                            isDragging = false
                            lastAnnouncedValue = targetValue
                            currentOnValueChange(targetValue)
                            dragJob?.cancel()
                            dragJob = scope.launch {
                                rulerPosition.animateTo(
                                    targetValue = targetValue.toFloat(),
                                    animationSpec = spring(stiffness = 640f, dampingRatio = 0.74f),
                                )
                            }
                        },
                    ),
            )
        }

        Column(
            modifier = Modifier.widthIn(min = 50.dp, max = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = displayedValue.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = valueCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InstallmentRulerCanvas(
    position: Float,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tickSpacingPx = with(density) { 18.dp.toPx() }
    val minorTickHeight = with(density) { 10.dp.toPx() }
    val majorTickHeight = with(density) { 16.dp.toPx() }
    val selectedTickHeight = with(density) { 22.dp.toPx() }
    val minorStrokeWidth = with(density) { 1.2.dp.toPx() }
    val majorStrokeWidth = with(density) { 1.8.dp.toPx() }
    val selectedStrokeWidth = with(density) { 2.4.dp.toPx() }
    val centerIndicatorWidth = with(density) { 3.dp.toPx() }
    val labelOffset = with(density) { 24.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = 14.dp.toPx()
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = minorStrokeWidth,
            cap = StrokeCap.Round,
        )

        valueRange.forEach { tick ->
            val x = centerX + (tick - position) * tickSpacingPx
            if (x < -tickSpacingPx || x > size.width + tickSpacingPx) return@forEach

            val distanceFromCenter = abs(tick - position)
            val isSelected = tick == position.roundToInt().coerceIn(valueRange.first, valueRange.last)
            val isMajor = tick == valueRange.first || tick % 5 == 0 || tick == valueRange.last
            val alpha = (1f - distanceFromCenter / 8f).coerceIn(0.22f, 1f)
            val tickHeight = when {
                isSelected -> selectedTickHeight
                isMajor -> majorTickHeight
                else -> minorTickHeight
            }
            val strokeWidth = when {
                isSelected -> selectedStrokeWidth
                isMajor -> majorStrokeWidth
                else -> minorStrokeWidth
            }
            val color = if (isSelected) selectedColor else tickColor.copy(alpha = alpha)

            drawLine(
                color = color,
                start = Offset(x, centerY - tickHeight / 2f),
                end = Offset(x, centerY + tickHeight / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            if (isSelected || isMajor) {
                val textLayout = textMeasurer.measure(
                    text = tick.toString(),
                    style = labelStyle.copy(
                        color = if (isSelected) selectedColor else labelStyle.color.copy(alpha = alpha),
                    ),
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x = x - textLayout.size.width / 2f,
                        y = labelOffset,
                    ),
                )
            }
        }

        drawLine(
            color = selectedColor,
            start = Offset(centerX, 3.dp.toPx()),
            end = Offset(centerX, size.height - 3.dp.toPx()),
            strokeWidth = centerIndicatorWidth,
            cap = StrokeCap.Round,
        )
    }
}
