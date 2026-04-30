package it.homebudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import it.homebudget.app.localization.AppStrings

internal fun fullMonthName(month: Int): String {
    return AppStrings.fullMonthName(month)
}

internal fun shortMonthName(month: Int): String {
    return AppStrings.shortMonthName(month)
}

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

    fun label(): String {
        return "${fullMonthName(month)} $year"
    }

    fun shortLabel(): String {
        val shortYear = (year % 100).toString().padStart(2, '0')
        return "${shortMonthName(month)} $shortYear"
    }
}

@Composable
fun MonthNavigationTitle(
    selectedMonth: MonthCursor,
    subtitle: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Column(
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
