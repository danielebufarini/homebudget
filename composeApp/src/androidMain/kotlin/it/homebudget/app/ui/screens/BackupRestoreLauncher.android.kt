package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
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
    val cloudBackupNotFoundLabel = stringResource(Res.string.cloud_backup_not_found)
    val driveAccessCancelledLabel = stringResource(Res.string.google_drive_access_cancelled)
    val driveAccessFailedLabel = stringResource(Res.string.google_drive_access_failed)
    val signInClient = remember(context) { createGoogleDriveBackupSignInClient(context) }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<BudgetBackupCounters?>(null) }

    fun prepareRestore(account: GoogleSignInAccount) {
        scope.launch {
            runCatching {
                downloadBackupFromGoogleDrive(context, account)
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
                onRestoreMessage(
                    when (error.message) {
                        "CLOUD_BACKUP_NOT_FOUND" -> cloudBackupNotFoundLabel
                        else -> error.message ?: invalidBackupLabel
                    }
                )
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
        }.getOrElse {
            val message = if (it is ApiException) {
                mapGoogleDriveSignInErrorMessage(
                    error = it,
                    driveAccessCancelledLabel = driveAccessCancelledLabel,
                    driveAccessFailedLabel = driveAccessFailedLabel
                )
            } else {
                it.message ?: driveAccessFailedLabel
            }
            onRestoreMessage(message)
            return@rememberLauncherForActivityResult
        }

        if (!hasGoogleDriveBackupAccess(account)) {
            onRestoreMessage(driveAccessFailedLabel)
            return@rememberLauncherForActivityResult
        }

        prepareRestore(account)
    }

    return remember(
        signInClient,
        repository,
        restoreBackupLabel,
        restoreLabel,
        cancelLabel,
        invalidBackupLabel,
        restoreFailedLabel,
        restoreConfirmationTemplate,
        restoreSuccessTemplate,
        pendingRestorePreview,
        pendingRestoreText,
        cloudBackupNotFoundLabel,
        driveAccessCancelledLabel,
        driveAccessFailedLabel
    ) {
        BackupRestoreLauncher(
            onOpen = {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (hasGoogleDriveBackupAccess(account)) {
                    prepareRestore(requireNotNull(account))
                } else {
                    signInLauncher.launch(signInClient.signInIntent)
                }
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
