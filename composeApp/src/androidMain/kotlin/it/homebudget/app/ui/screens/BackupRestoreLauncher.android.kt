package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.BudgetBackupCounters
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.parseBudgetBackup
import it.homebudget.app.data.restoreBudgetBackup
import it.homebudget.app.localization.formatResourceArgs
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal actual class BackupRestoreLauncher(
    private val onOpen: () -> Unit,
    private val renderContent: @Composable () -> Unit
) {
    actual fun open() {
        onOpen()
    }

    @Composable
    actual fun Render() {
        renderContent()
    }
}

@Composable
internal actual fun rememberBackupRestoreLauncher(
    onRestoreMessage: (String) -> Unit
): BackupRestoreLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: ExpenseRepository = koinInject()
    val restoreBackupLabel = stringResource(Res.string.restore_backup)
    val restoreLabel = stringResource(Res.string.restore)
    val cancelLabel = stringResource(Res.string.cancel)
    val invalidBackupLabel = stringResource(Res.string.backup_restore_invalid)
    val restoreFailedLabel = stringResource(Res.string.backup_restore_failed)
    val restoreConfirmationTemplate = stringResource(Res.string.backup_restore_confirmation_message)
    val restoreSuccessTemplate = stringResource(Res.string.backup_restore_success)
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<BudgetBackupCounters?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().decodeToString()
                } ?: error(invalidBackupLabel)
            }.onSuccess { backupText ->
                runCatching {
                    parseBudgetBackup(backupText)
                }.onSuccess { preview ->
                    pendingRestoreText = backupText
                    pendingRestorePreview = preview
                }.onFailure { error ->
                    onRestoreMessage(error.message ?: invalidBackupLabel)
                }
            }.onFailure { error ->
                onRestoreMessage(error.message ?: invalidBackupLabel)
            }
        }
    }

    return remember(
        filePicker,
        repository,
        restoreBackupLabel,
        restoreLabel,
        cancelLabel,
        invalidBackupLabel,
        restoreFailedLabel,
        restoreConfirmationTemplate,
        restoreSuccessTemplate,
        pendingRestorePreview,
        pendingRestoreText
    ) {
        BackupRestoreLauncher(
            onOpen = {
                filePicker.launch(arrayOf("application/json", "text/*"))
            },
            renderContent = {
                val preview = pendingRestorePreview
                val restoreText = pendingRestoreText
                if (preview != null && restoreText != null) {
                    AlertDialog(
                        onDismissRequest = {
                            pendingRestorePreview = null
                            pendingRestoreText = null
                        },
                        title = { Text(restoreBackupLabel) },
                        text = {
                            Text(
                                restoreConfirmationTemplate
                                    .formatResourceArgs(
                                        preview.categoriesCount,
                                        preview.expensesCount,
                                        preview.incomesCount
                                    )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                colors = homeBudgetTextButtonColors(),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            restoreBudgetBackup(repository, restoreText)
                                        }.onSuccess { result ->
                                            onRestoreMessage(
                                                restoreSuccessTemplate
                                                    .formatResourceArgs(
                                                        result.categoriesCount,
                                                        result.expensesCount,
                                                        result.incomesCount
                                                    )
                                            )
                                        }.onFailure { error ->
                                            onRestoreMessage(error.message ?: restoreFailedLabel)
                                        }
                                        pendingRestorePreview = null
                                        pendingRestoreText = null
                                    }
                                }
                            ) {
                                Text(restoreLabel)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    pendingRestorePreview = null
                                    pendingRestoreText = null
                                },
                                colors = homeBudgetTextButtonColors()
                            ) {
                                Text(cancelLabel)
                            }
                        }
                    )
                }
            }
        )
    }
}
