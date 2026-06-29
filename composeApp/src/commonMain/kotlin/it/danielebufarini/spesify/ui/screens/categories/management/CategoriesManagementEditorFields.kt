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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME

@Composable
internal fun CategoryNameEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    iconKey: String,
    accentColor: Color,
    palette: CategoriesPalette,
    focusRequester: FocusRequester,
    onKeyboardDone: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) {
        accentColor
    } else {
        palette.textMuted.copy(alpha = 0.35f)
    }

    Surface(
        modifier = modifier.clickable {
            focusRequester.requestFocus()
            onFocused()
        },
        shape = RoundedCornerShape(16.dp),
        color = palette.glassSurfaceSoft,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = iconForKey(iconKey),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    color = if (isFocused) palette.textSecondary else palette.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                onFocused()
                            }
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onKeyboardDone() }),
                )
            }
            Text(
                text = "${value.length} / 24",
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryIconPicker(
    label: String,
    iconKey: String,
    selectedColor: Color,
    palette: CategoriesPalette,
    onIconKeyChange: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.pointerInput(Unit) {
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
                val selected = iconKey == option.key
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .clickable { onIconKeyChange(option.key) },
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
internal fun CategoryColorPicker(
    label: String,
    palette: CategoriesPalette,
    selectedColorIndex: Int,
    onColorIndexChange: (Int) -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.pointerInput(Unit) {
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
                        .clickable { onColorIndexChange(index) },
                    shape = CircleShape,
                    color = color,
                    border = if (selected) {
                        BorderStroke(
                            width = 3.dp,
                            color = Color.White.copy(alpha = 0.9f),
                        )
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
internal fun CategoryTypePicker(
    categoryType: String,
    expenseLabel: String,
    incomeLabel: String,
    selectedColor: Color,
    palette: CategoriesPalette,
    onCategoryTypeChange: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
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
            CategoryTypeChip(
                selected = categoryType == CATEGORY_TYPE_EXPENSE,
                label = expenseLabel,
                selectedColor = selectedColor,
                palette = palette,
                onClick = { onCategoryTypeChange(CATEGORY_TYPE_EXPENSE) },
                icon = { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
            )
            CategoryTypeChip(
                selected = categoryType == CATEGORY_TYPE_INCOME,
                label = incomeLabel,
                selectedColor = selectedColor,
                palette = palette,
                onClick = { onCategoryTypeChange(CATEGORY_TYPE_INCOME) },
                icon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryTypeChip(
    selected: Boolean,
    label: String,
    selectedColor: Color,
    palette: CategoriesPalette,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                selectedColor.copy(alpha = 0.18f)
            } else {
                Color.White.copy(alpha = 0.04f)
            },
            labelColor = if (selected) palette.textPrimary else palette.textMuted,
            leadingIconContentColor = if (selected) selectedColor else palette.textMuted,
        ),
        border = null,
        modifier = modifier,
    )
}
