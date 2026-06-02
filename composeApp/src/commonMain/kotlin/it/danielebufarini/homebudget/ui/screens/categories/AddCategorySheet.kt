package it.danielebufarini.homebudget.ui.screens.categories

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.category_name
import homebudget.composeapp.generated.resources.done
import homebudget.composeapp.generated.resources.icon
import it.danielebufarini.homebudget.ui.screens.dismissPlatformKeyboard
import it.danielebufarini.homebudget.ui.screens.transactions.CategoryPreviewCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftActionBar
import it.danielebufarini.homebudget.ui.screens.transactions.SoftSectionCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftTextField
import kotlinx.coroutines.delay
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
    val doneLabel = stringResource(Res.string.done)
    val iconLabel = stringResource(Res.string.icon)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var categoryName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIconKey by remember(initialIconKey) {
        mutableStateOf(normalizeCategoryIconKey(initialIconKey))
    }
    var textFieldGeneration by remember { mutableIntStateOf(0) }
    var keyboardDismissGeneration by remember { mutableIntStateOf(0) }
    val trimmedCategoryName = categoryName.trim()
    fun dismissKeyboard() {
        textFieldGeneration += 1
        keyboardDismissGeneration += 1
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        dismissPlatformKeyboard()
    }

    LaunchedEffect(initialName, initialIconKey) {
        categoryName = initialName
        selectedIconKey = normalizeCategoryIconKey(initialIconKey)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = ::dismissKeyboard) {
                            Text(doneLabel)
                        }
                    }

                    CategoryPreviewCard(
                        name = trimmedCategoryName,
                        iconKey = selectedIconKey,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures { dismissKeyboard() }
                        }
                    )

                    key(textFieldGeneration) {
                        SoftTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = categoryNameLabel,
                            leadingIcon = Icons.Filled.Category,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { dismissKeyboard() }
                            )
                        )
                    }

                    SoftSectionCard(title = iconLabel) {
                        CategoryIconPicker(
                            selectedIconKey = selectedIconKey,
                            onIconSelected = { iconKey ->
                                selectedIconKey = iconKey
                                dismissKeyboard()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                SoftActionBar(
                    cancelLabel = cancelLabel,
                    confirmLabel = confirmLabel,
                    confirmEnabled = trimmedCategoryName.isNotEmpty(),
                    onCancel = {
                        dismissKeyboard()
                        onDismiss()
                    },
                    onConfirm = {
                        dismissKeyboard()
                        onConfirm(trimmedCategoryName, normalizeCategoryIconKey(selectedIconKey))
                    }
                )
            }
        }
    }
}
