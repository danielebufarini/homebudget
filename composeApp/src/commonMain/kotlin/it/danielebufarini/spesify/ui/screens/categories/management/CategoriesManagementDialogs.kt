package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.cancel
import spesify.composeapp.generated.resources.categories_archive_action
import spesify.composeapp.generated.resources.categories_delete_dialog_description
import spesify.composeapp.generated.resources.categories_delete_dialog_title
import spesify.composeapp.generated.resources.categories_move_confirm
import spesify.composeapp.generated.resources.categories_move_picker_description
import spesify.composeapp.generated.resources.categories_move_picker_empty
import spesify.composeapp.generated.resources.categories_move_picker_title
import spesify.composeapp.generated.resources.categories_move_transactions_action
import spesify.composeapp.generated.resources.categories_used_delete_message_plural
import spesify.composeapp.generated.resources.categories_used_delete_message_single
import spesify.composeapp.generated.resources.categories_used_delete_prompt
import spesify.composeapp.generated.resources.delete

@Composable
internal fun DeleteCategoryDialog(
    category: CategoryUiModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val deleteDialogTitle = stringResource(Res.string.categories_delete_dialog_title, category.name)
    val deleteDialogDescription = stringResource(Res.string.categories_delete_dialog_description)
    val deleteLabel = stringResource(Res.string.delete)
    val cancelLabel = stringResource(Res.string.cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sheetBackground,
        textContentColor = palette.textSecondary,
        titleContentColor = palette.textPrimary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = DeleteRed,
            )
        },
        title = { Text(deleteDialogTitle) },
        text = { Text(deleteDialogDescription) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
            ) {
                Text(deleteLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

@Composable
internal fun UsedCategoryDeleteDialog(
    category: CategoryUiModel,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onMoveTransactions: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val dialogTitle = stringResource(Res.string.categories_delete_dialog_title, category.name)
    val usageMessage = if (category.transactionCount == 1) {
        stringResource(Res.string.categories_used_delete_message_single, category.transactionCount)
    } else {
        stringResource(Res.string.categories_used_delete_message_plural, category.transactionCount)
    }
    val prompt = stringResource(Res.string.categories_used_delete_prompt)
    val archiveLabel = stringResource(Res.string.categories_archive_action)
    val moveTransactionsLabel = stringResource(Res.string.categories_move_transactions_action)
    val cancelLabel = stringResource(Res.string.cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sheetBackground,
        textContentColor = palette.textSecondary,
        titleContentColor = palette.textPrimary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = DeleteRed,
            )
        },
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(usageMessage)
                Text(prompt)
                CategoryDialogActionButton(
                    label = archiveLabel,
                    onClick = onArchive,
                )
                CategoryDialogActionButton(
                    label = moveTransactionsLabel,
                    onClick = onMoveTransactions,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveCategoryTransactionsSheet(
    sourceCategory: CategoryUiModel,
    availableCategories: List<CategoryUiModel>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val title = stringResource(Res.string.categories_move_picker_title)
    val description = stringResource(Res.string.categories_move_picker_description)
    val emptyMessage = stringResource(Res.string.categories_move_picker_empty)
    val moveLabel = stringResource(Res.string.categories_move_confirm)
    val cancelLabel = stringResource(Res.string.cancel)
    var selectedCategoryId by rememberSaveable(sourceCategory.id) {
        mutableStateOf(availableCategories.firstOrNull()?.id.orEmpty())
    }

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
                .padding(horizontal = 22.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = description,
                color = palette.textSecondary,
            )

            if (availableCategories.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = palette.glassSurfaceSoft,
                ) {
                    Text(
                        text = emptyMessage,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(
                        count = availableCategories.size,
                        key = { index -> availableCategories[index].id },
                    ) { index ->
                        val category = availableCategories[index]
                        val selected = selectedCategoryId == category.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryId = category.id },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) {
                                category.accent.copy(alpha = 0.18f)
                            } else {
                                palette.glassSurfaceSoft
                            },
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) category.accent else palette.cardBorder,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = category.accent.copy(alpha = 0.16f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconForKey(category.iconKey),
                                            contentDescription = null,
                                            tint = category.accent,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.name,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = category.usageLabel(),
                                        color = palette.textSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = null,
                                        tint = category.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(cancelLabel)
                }
                Button(
                    onClick = { onConfirm(selectedCategoryId) },
                    enabled = selectedCategoryId.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(moveLabel)
                }
            }
        }
    }
}

@Composable
internal fun CategoryDialogActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
