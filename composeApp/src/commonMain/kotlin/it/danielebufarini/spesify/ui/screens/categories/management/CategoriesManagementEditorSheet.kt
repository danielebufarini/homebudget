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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.ui.screens.dismissPlatformKeyboard
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.categories_archive_action
import spesify.composeapp.generated.resources.categories_archive_sheet_description
import spesify.composeapp.generated.resources.categories_archive_sheet_title
import spesify.composeapp.generated.resources.categories_choose_color
import spesify.composeapp.generated.resources.categories_choose_icon
import spesify.composeapp.generated.resources.categories_close_content_description
import spesify.composeapp.generated.resources.categories_delete_sheet_description
import spesify.composeapp.generated.resources.categories_delete_sheet_title
import spesify.composeapp.generated.resources.categories_new_title
import spesify.composeapp.generated.resources.categories_save_changes
import spesify.composeapp.generated.resources.category_name
import spesify.composeapp.generated.resources.delete
import spesify.composeapp.generated.resources.edit_category
import spesify.composeapp.generated.resources.expense_label
import spesify.composeapp.generated.resources.income_label

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
    val nameFocusRequester = remember { FocusRequester() }
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
    var textFieldGeneration by remember { mutableIntStateOf(0) }
    var keyboardDismissGeneration by remember { mutableIntStateOf(0) }

    val canSave = name.trim().isNotBlank()

    fun dismissKeyboard() {
        textFieldGeneration += 1
        keyboardDismissGeneration += 1
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        dismissPlatformKeyboard()
    }

    LaunchedEffect(category.id) {
        delay(250)
        nameFocusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(keyboardDismissGeneration) {
        if (keyboardDismissGeneration == 0) {
            return@LaunchedEffect
        }

        delay(30)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        dismissPlatformKeyboard()

        delay(120)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        dismissPlatformKeyboard()
    }

    ModalBottomSheet(
        onDismissRequest = {
            dismissKeyboard()
            onDismiss()
        },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { dismissKeyboard() }
                },
            ) {
                Text(
                    text = if (category.id.isBlank()) newCategoryLabel else editCategoryLabel,
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        dismissKeyboard()
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = closeContentDescription,
                        tint = palette.textPrimary,
                    )
                }
            }

            key(textFieldGeneration) {
                CategoryNameEditorField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    label = categoryNameLabel,
                    iconKey = iconKey,
                    accentColor = CategoryAccentPalette[selectedColorIndex],
                    palette = palette,
                    focusRequester = nameFocusRequester,
                    onKeyboardDone = ::dismissKeyboard,
                    onFocused = {
                        keyboardController?.show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { dismissKeyboard() }
                },
            ) {
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
                                .clickable {
                                    iconKey = option.key
                                    dismissKeyboard()
                                },
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

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { dismissKeyboard() }
                },
            ) {
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
                                .clickable {
                                    selectedColorIndex = index
                                    dismissKeyboard()
                                },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { dismissKeyboard() }
                    },
                shape = RoundedCornerShape(22.dp),
                color = palette.glassSurfaceSoft,
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = {
                            categoryType = CATEGORY_TYPE_EXPENSE
                            dismissKeyboard()
                        },
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
                        onClick = {
                            categoryType = CATEGORY_TYPE_INCOME
                            dismissKeyboard()
                        },
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

            CategoryEditorDeleteSection(
                visible = category.id.isNotBlank(),
                isUsedCategory = isUsedCategory,
                palette = palette,
                archiveCategorySheetTitle = archiveCategorySheetTitle,
                archiveCategorySheetDescription = archiveCategorySheetDescription,
                deleteCategorySheetTitle = deleteCategorySheetTitle,
                deleteCategorySheetDescription = deleteCategorySheetDescription,
                archiveLabel = archiveLabel,
                deleteLabel = deleteLabel,
                onDelete = {
                    dismissKeyboard()
                    onDelete()
                },
            )

            CategoryEditorSaveButton(
                label = saveChangesLabel,
                enabled = canSave,
                color = CategoryAccentPalette[selectedColorIndex],
                onClick = {
                    dismissKeyboard()
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
            )
        }
    }
}

@Composable
private fun CategoryNameEditorField(
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
