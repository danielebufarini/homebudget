package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                    .heightIn(max = sheetMaxHeight * 0.92f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = sheetMaxHeight * 0.72f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )

                    CategoryPreviewCard(
                        name = trimmedCategoryName,
                        iconKey = selectedIconKey
                    )

                    SoftTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = categoryNameLabel,
                        leadingIcon = Icons.Filled.Category,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SoftSectionCard(title = "Icon") {
                        CategoryIconPicker(
                            selectedIconKey = selectedIconKey,
                            onIconSelected = { selectedIconKey = it },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                SoftActionBar(
                    cancelLabel = cancelLabel,
                    confirmLabel = confirmLabel,
                    confirmEnabled = trimmedCategoryName.isNotEmpty(),
                    onCancel = onDismiss,
                    onConfirm = {
                        onConfirm(trimmedCategoryName, normalizeCategoryIconKey(selectedIconKey))
                    }
                )
            }
        }
    }
}
