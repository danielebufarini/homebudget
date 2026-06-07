package it.danielebufarini.spesify.ui.screens.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import org.jetbrains.compose.resources.stringArrayResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.full_month_names
import spesify.composeapp.generated.resources.short_month_names

data class MonthCursor(
    val year: Int,
    val month: Int
) {
    fun previous(): MonthCursor {
        return if (month == 1) MonthCursor(year - 1, 12) else MonthCursor(year, month - 1)
    }

    fun next(): MonthCursor {
        return if (month == 12) MonthCursor(year + 1, 1) else MonthCursor(year, month + 1)
    }

    fun trailingMonths(count: Int): List<MonthCursor> {
        if (count <= 0) {
            return emptyList()
        }

        val months = ArrayDeque<MonthCursor>(count)
        var cursor = this
        repeat(count) {
            months.addFirst(cursor)
            cursor = cursor.previous()
        }
        return months.toList()
    }
}

@Composable
fun MonthCursor.label(): String {
    val fullMonthNames = stringArrayResource(Res.array.full_month_names)
    return "${fullMonthNames[month - 1]} $year"
}

@Composable
fun MonthCursor.shortLabel(): String {
    val shortMonthNames = stringArrayResource(Res.array.short_month_names)
    val shortYear = (year % 100).toString().padStart(2, '0')
    return "${shortMonthNames[month - 1]} $shortYear"
}

@Composable
fun MonthNavigationTitle(
    selectedMonth: MonthCursor,
    subtitle: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    useIosGlassStyle: Boolean = false
) {
    if (useIosGlassStyle && rememberIsIosPlatform()) {
        IosMonthNavigationTitle(
            selectedMonth = selectedMonth,
            subtitle = subtitle,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthArrowButton(direction = ArrowDirection.Left, onClick = onPreviousMonth)
            Text(
                text = selectedMonth.label(),
                style = MaterialTheme.typography.titleLarge
            )
            MonthArrowButton(direction = ArrowDirection.Right, onClick = onNextMonth)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IosMonthNavigationTitle(
    selectedMonth: MonthCursor,
    subtitle: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthArrowButton(direction = ArrowDirection.Left, onClick = onPreviousMonth)
                Text(
                    text = selectedMonth.label(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                MonthArrowButton(direction = ArrowDirection.Right, onClick = onNextMonth)
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthArrowButton(
    direction: ArrowDirection,
    onClick: () -> Unit
) {
    val arrowColor = MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp)
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val path = Path().apply {
                if (direction == ArrowDirection.Left) {
                    moveTo(size.width * 0.75f, size.height * 0.15f)
                    lineTo(size.width * 0.3f, size.height * 0.5f)
                    lineTo(size.width * 0.75f, size.height * 0.85f)
                } else {
                    moveTo(size.width * 0.25f, size.height * 0.15f)
                    lineTo(size.width * 0.7f, size.height * 0.5f)
                    lineTo(size.width * 0.25f, size.height * 0.85f)
                }
            }
            drawPath(
                path = path,
                color = arrowColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

enum class ArrowDirection {
    Left,
    Right
}
