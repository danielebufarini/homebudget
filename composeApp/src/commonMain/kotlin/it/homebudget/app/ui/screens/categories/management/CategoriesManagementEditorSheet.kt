package it.homebudget.app.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories_archive_action
import homebudget.composeapp.generated.resources.categories_archive_sheet_description
import homebudget.composeapp.generated.resources.categories_archive_sheet_title
import homebudget.composeapp.generated.resources.categories_choose_color
import homebudget.composeapp.generated.resources.categories_choose_icon
import homebudget.composeapp.generated.resources.categories_close_content_description
import homebudget.composeapp.generated.resources.categories_delete_sheet_description
import homebudget.composeapp.generated.resources.categories_delete_sheet_title
import homebudget.composeapp.generated.resources.categories_new_title
import homebudget.composeapp.generated.resources.categories_save_changes
import homebudget.composeapp.generated.resources.category_name
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.edit_category
import homebudget.composeapp.generated.resources.expense_label
import homebudget.composeapp.generated.resources.income_label
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.CATEGORY_TYPE_INCOME
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditorSheet(
    category: CategoryUiModel,
    onDismiss: () -> Unit,
    onSave: (CategoryUiModel) -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isUsedCategory = category.transactionCount > 0
    val newCategoryLabel = stringResource(Res.string.categories_new_title)
    val editCategoryLabel = stringResource(Res.string.edit_category)
    val closeContentDescription = stringResource(Res.string.categories_close_content_description)
    val categoryNameLabel = stringResource(Res.string.category_name)
    val chooseIconLabel = stringResource(Res.string.categories_choose_icon)
    val chooseColorLabel = stringResource(Res.string.categories_choose_color)
    val expenseLabel = stringResource(Res.string.expense_label)
    val incomeLabel = stringResource(Res.string.income_label)
    val archiveCategorySheetTitle = stringResource(Res.string.categories_archive_sheet_title)
    val archiveCategorySheetDescription = stringResource(Res.string.categories_archive_sheet_description)
    val deleteCategorySheetTitle = stringResource(Res.string.categories_delete_sheet_title)
    val deleteCategorySheetDescription = stringResource(Res.string.categories_delete_sheet_description)
    val archiveLabel = stringResource(Res.string.categories_archive_action)
    val deleteLabel = stringResource(Res.string.delete)
    val saveChangesLabel = stringResource(Res.string.categories_save_changes)
    var name by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var iconKey by rememberSaveable(category.id) { mutableStateOf(category.iconKey.ifBlank { "category" }) }
    var categoryType by rememberSaveable(category.id) { mutableStateOf(category.categoryType) }
    var selectedColorIndex by rememberSaveable(category.id) {
        mutableIntStateOf(
            CategoryAccentPaletteHex.indexOf(category.colorHex)
                .takeIf { it >= 0 } ?: 0,
        )
    }

    val canSave = name.trim().isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.sheetBackground,
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (category.id.isBlank()) newCategoryLabel else editCategoryLabel,
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = closeContentDescription,
                        tint = palette.textPrimary,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                label = { Text(categoryNameLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    },
                ),
                leadingIcon = {
                    Icon(
                        imageVector = iconForKey(iconKey),
                        contentDescription = null,
                        tint = CategoryAccentPalette[selectedColorIndex],
                    )
                },
                trailingIcon = {
                    Text(
                        text = "${name.length} / 24",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CategoryAccentPalette[selectedColorIndex],
                    unfocusedBorderColor = palette.textMuted.copy(alpha = 0.35f),
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedLabelColor = palette.textSecondary,
                    unfocusedLabelColor = palette.textMuted,
                    focusedContainerColor = palette.glassSurfaceSoft,
                    unfocusedContainerColor = palette.glassSurfaceSoft,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = chooseIconLabel,
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
                                .clickable { iconKey = option.key },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                CategoryAccentPalette[selectedColorIndex]
                            } else {
                                palette.glassSurfaceSoft
                            },
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = chooseColorLabel,
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
                                .clickable { selectedColorIndex = index },
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = palette.glassSurfaceSoft,
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { categoryType = CATEGORY_TYPE_EXPENSE },
                        label = { Text(expenseLabel) },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                CategoryAccentPalette[selectedColorIndex].copy(alpha = 0.18f)
                            } else {
                                Color.White.copy(alpha = 0.04f)
                            },
                            labelColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                palette.textPrimary
                            } else {
                                palette.textMuted
                            },
                            leadingIconContentColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                CategoryAccentPalette[selectedColorIndex]
                            } else {
                                palette.textMuted
                            },
                        ),
                        border = null,
                        modifier = Modifier.weight(1f),
                    )
                    AssistChip(
                        onClick = { categoryType = CATEGORY_TYPE_INCOME },
                        label = { Text(incomeLabel) },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                CategoryAccentPalette[selectedColorIndex].copy(alpha = 0.18f)
                            } else {
                                Color.White.copy(alpha = 0.04f)
                            },
                            labelColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                palette.textPrimary
                            } else {
                                palette.textMuted
                            },
                            leadingIconContentColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                CategoryAccentPalette[selectedColorIndex]
                            } else {
                                palette.textMuted
                            },
                        ),
                        border = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (category.id.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = DeleteRed.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = DeleteRed,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUsedCategory) {
                                    archiveCategorySheetTitle
                                } else {
                                    deleteCategorySheetTitle
                                },
                                color = DeleteRed,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isUsedCategory) {
                                    archiveCategorySheetDescription
                                } else {
                                    deleteCategorySheetDescription
                                },
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = onDelete) {
                            Text(
                                text = if (isUsedCategory) archiveLabel else deleteLabel,
                                color = DeleteRed,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSave(
                        category.copy(
                            name = name.trim(),
                            iconKey = iconKey,
                            colorHex = CategoryAccentPaletteHex[selectedColorIndex],
                            categoryType = categoryType,
                            accent = CategoryAccentPalette[selectedColorIndex],
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CategoryAccentPalette[selectedColorIndex],
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = saveChangesLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
