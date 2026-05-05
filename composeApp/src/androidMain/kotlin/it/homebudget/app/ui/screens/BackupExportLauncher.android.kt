package it.homebudget.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.exportBudgetBackup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal actual class BackupExportLauncher(
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
internal actual fun rememberBackupExportLauncher(
    onExportMessage: (String) -> Unit
): BackupExportLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: ExpenseRepository = koinInject()
    val backupExportFailedLabel = stringResource(Res.string.backup_export_failed)
    val backupExportSavedLabel = stringResource(Res.string.backup_export_saved)
    val driveAccessCancelledLabel = stringResource(Res.string.google_drive_access_cancelled)
    val driveAccessFailedLabel = stringResource(Res.string.google_drive_access_failed)
    val signInClient = remember(context) { createGoogleDriveBackupSignInClient(context) }

    fun runExport(account: GoogleSignInAccount) {
        scope.launch {
            runCatching {
                val backup = exportBudgetBackup(repository)
                uploadBackupToGoogleDrive(context, account, backup)
            }.onSuccess {
                onExportMessage(backupExportSavedLabel)
            }.onFailure { error ->
                onExportMessage(error.message ?: backupExportFailedLabel)
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onExportMessage(driveAccessCancelledLabel)
            return@rememberLauncherForActivityResult
        }

        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
        }.getOrElse {
            onExportMessage(it.message ?: driveAccessFailedLabel)
            return@rememberLauncherForActivityResult
        }

        if (!hasGoogleDriveBackupAccess(account)) {
            onExportMessage(driveAccessFailedLabel)
            return@rememberLauncherForActivityResult
        }

        runExport(account)
    }

    return remember(
        signInClient,
        repository,
        backupExportFailedLabel,
        backupExportSavedLabel,
        driveAccessCancelledLabel,
        driveAccessFailedLabel
    ) {
        BackupExportLauncher(
            onOpen = {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (hasGoogleDriveBackupAccess(account)) {
                    runExport(requireNotNull(account))
                } else {
                    signInLauncher.launch(signInClient.signInIntent)
                }
            },
            renderContent = {}
        )
    }
}
