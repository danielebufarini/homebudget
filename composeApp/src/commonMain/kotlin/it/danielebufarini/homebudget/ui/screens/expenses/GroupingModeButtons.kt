package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.homebudget.ui.screens.ExpenseGroupingMode
import it.danielebufarini.homebudget.ui.screens.platform.homeBudgetFilledTonalButtonColors
import it.danielebufarini.homebudget.ui.screens.platform.homeBudgetOutlinedButtonColors
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform

@Composable
internal fun GroupingModeButtons(
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    byCategoryLabel: String,
    byDateLabel: String,
    modifier: Modifier = Modifier
) {
    if (!rememberIsIosPlatform()) {
        AndroidGroupingModeSegmentedButtons(
            groupingMode = groupingMode,
            onGroupingModeChange = onGroupingModeChange,
            byCategoryLabel = byCategoryLabel,
            byDateLabel = byDateLabel,
            modifier = modifier
        )
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupingModeButton(
            label = byCategoryLabel,
            selected = groupingMode == ExpenseGroupingMode.ByCategory,
            onClick = { onGroupingModeChange(ExpenseGroupingMode.ByCategory) }
        )
        GroupingModeButton(
            label = byDateLabel,
            selected = groupingMode == ExpenseGroupingMode.ByDate,
            onClick = { onGroupingModeChange(ExpenseGroupingMode.ByDate) }
        )
    }
}

@Composable
private fun AndroidGroupingModeSegmentedButtons(
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    byCategoryLabel: String,
    byDateLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidGroupingModeButton(
                label = byCategoryLabel,
                selected = groupingMode == ExpenseGroupingMode.ByCategory,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByCategory) }
            )
            AndroidGroupingModeButton(
                label = byDateLabel,
                selected = groupingMode == ExpenseGroupingMode.ByDate,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByDate) }
            )
        }
    }
}

@Composable
private fun AndroidGroupingModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label)
        }
    }
}

@Composable
private fun GroupingModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            colors = homeBudgetFilledTonalButtonColors(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = homeBudgetOutlinedButtonColors(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label)
        }
    }
}
