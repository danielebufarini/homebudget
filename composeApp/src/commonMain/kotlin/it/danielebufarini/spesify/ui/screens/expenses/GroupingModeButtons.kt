package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.ui.screens.ExpenseGroupingMode
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.transaction_view_mode_content_description

@Composable
internal fun GroupingModeButtons(
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    byCategoryLabel: String,
    byDateLabel: String,
    modifier: Modifier = Modifier
) {
    TransactionViewModeCarousel(
        selectedMode = groupingMode,
        modes = listOf(
            ExpenseGroupingMode.ByCategory to byCategoryLabel,
            ExpenseGroupingMode.ByDate to byDateLabel
        ),
        onModeSelected = onGroupingModeChange,
        modifier = modifier
    )
}

@Composable
internal fun TransactionViewModeCarousel(
    selectedMode: ExpenseGroupingMode,
    modes: List<Pair<ExpenseGroupingMode, String>>,
    onModeSelected: (ExpenseGroupingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.28f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
        )
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { (mode, label) ->
                TransactionViewModeCarouselItem(
                    label = label,
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun TransactionViewModeCarouselItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "TransactionViewModeContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "TransactionViewModeContentColor"
    )
    val accessibilityLabel = stringResource(
        Res.string.transaction_view_mode_content_description,
        label
    )

    Surface(
        modifier = Modifier
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .defaultMinSize(minWidth = 72.dp, minHeight = 40.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = accessibilityLabel
            },
        shape = RoundedCornerShape(19.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
        } else {
            null
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            color = contentColor
        )
    }
}
