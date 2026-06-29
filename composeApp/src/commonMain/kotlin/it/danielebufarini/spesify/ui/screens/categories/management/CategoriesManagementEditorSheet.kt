package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
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

            CategoryIconPicker(
                label = chooseIconLabel,
                iconKey = iconKey,
                selectedColor = CategoryAccentPalette[selectedColorIndex],
                palette = palette,
                onIconKeyChange = {
                    iconKey = it
                    dismissKeyboard()
                },
                onDismissKeyboard = ::dismissKeyboard,
            )

            CategoryColorPicker(
                label = chooseColorLabel,
                palette = palette,
                selectedColorIndex = selectedColorIndex,
                onColorIndexChange = {
                    selectedColorIndex = it
                    dismissKeyboard()
                },
                onDismissKeyboard = ::dismissKeyboard,
            )

            CategoryTypePicker(
                categoryType = categoryType,
                expenseLabel = expenseLabel,
                incomeLabel = incomeLabel,
                selectedColor = CategoryAccentPalette[selectedColorIndex],
                palette = palette,
                onCategoryTypeChange = {
                    categoryType = it
                    dismissKeyboard()
                },
                onDismissKeyboard = ::dismissKeyboard,
            )

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
