package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.category_name
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddCategorySheet(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialIconKey: String = DEFAULT_CATEGORY_ICON_KEY,
    onConfirm: (String, String) -> Unit
) {
    val cancelLabel = stringResource(Res.string.cancel)
    val categoryNameLabel = stringResource(Res.string.category_name)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIconKey by remember(initialIconKey) {
        mutableStateOf(normalizeCategoryIconKey(initialIconKey))
    }
    val trimmedCategoryName = categoryName.trim()

    LaunchedEffect(initialName, initialIconKey) {
        categoryName = initialName
        selectedIconKey = normalizeCategoryIconKey(initialIconKey)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            val sheetMaxHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight * 0.9f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = sheetMaxHeight * 0.7f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    PlatformTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = categoryNameLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CategoryIconPicker(
                        selectedIconKey = selectedIconKey,
                        onIconSelected = { selectedIconKey = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = homeBudgetTextButtonColors()
                    ) {
                        Text(cancelLabel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = trimmedCategoryName.isNotEmpty(),
                        onClick = {
                            onConfirm(trimmedCategoryName, normalizeCategoryIconKey(selectedIconKey))
                        },
                        colors = homeBudgetButtonColors()
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}
