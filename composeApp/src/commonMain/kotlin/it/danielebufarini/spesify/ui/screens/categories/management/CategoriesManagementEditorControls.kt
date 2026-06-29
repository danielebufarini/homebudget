package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditorIconPicker(
    label: String,
    selectedIconKey: String,
    selectedColor: Color,
    palette: CategoriesPalette,
    onIconSelected: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { onDismissKeyboard() }
        },
    ) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EditorIconOptions.forEach { option ->
                val selected = selectedIconKey == option.key
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .clickable { onIconSelected(option.key) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) selectedColor else palette.glassSurfaceSoft,
                    tonalElevation = if (selected) 8.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.key,
                            tint = if (selected) Color.White else palette.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryEditorColorPicker(
    label: String,
    selectedColorIndex: Int,
    palette: CategoriesPalette,
    onColorSelected: (Int) -> Unit,
    onDismissKeyboard: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { onDismissKeyboard() }
        },
    ) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CategoryAccentPalette.forEachIndexed { index, color ->
                val selected = selectedColorIndex == index
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onColorSelected(index) },
                    shape = CircleShape,
                    color = color,
                    border = if (selected) {
                        BorderStroke(3.dp, Color.White.copy(alpha = 0.9f))
                    } else {
                        null
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryEditorTypeSelector(
    categoryType: String,
    selectedColor: Color,
    palette: CategoriesPalette,
    expenseLabel: String,
    incomeLabel: String,
    onCategoryTypeChanged: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { onDismissKeyboard() }
            },
        shape = RoundedCornerShape(22.dp),
        color = palette.glassSurfaceSoft,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryEditorTypeChip(
                label = expenseLabel,
                selected = categoryType == CATEGORY_TYPE_EXPENSE,
                selectedColor = selectedColor,
                palette = palette,
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)) },
                onClick = { onCategoryTypeChanged(CATEGORY_TYPE_EXPENSE) },
                modifier = Modifier.weight(1f),
            )
            CategoryEditorTypeChip(
                label = incomeLabel,
                selected = categoryType == CATEGORY_TYPE_INCOME,
                selectedColor = selectedColor,
                palette = palette,
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) },
                onClick = { onCategoryTypeChanged(CATEGORY_TYPE_INCOME) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryEditorTypeChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    palette: CategoriesPalette,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) selectedColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
            labelColor = if (selected) palette.textPrimary else palette.textMuted,
            leadingIconContentColor = if (selected) selectedColor else palette.textMuted,
        ),
        border = null,
        modifier = modifier,
    )
}
