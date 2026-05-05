package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_export_failed
import homebudget.composeapp.generated.resources.backup_export_saved
import it.homebudget.app.data.BudgetBackupFile
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
    var pendingExport by remember { mutableStateOf<BudgetBackupFile?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val exportFile = pendingExport
        pendingExport = null

        if (uri == null || exportFile == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportFile.content.encodeToByteArray())
                } ?: error(backupExportFailedLabel)
            }

            onExportMessage(
                if (result.isSuccess) backupExportSavedLabel else backupExportFailedLabel
            )
        }
    }

    return remember(saveLauncher, repository, backupExportFailedLabel, backupExportSavedLabel) {
        BackupExportLauncher(
            onOpen = {
                scope.launch {
                    runCatching {
                        exportBudgetBackup(repository)
                    }.onSuccess { exportFile ->
                        pendingExport = exportFile
                        saveLauncher.launch(exportFile.fileName)
                    }.onFailure { error ->
                        onExportMessage(error.message ?: backupExportFailedLabel)
                    }
                }
            },
            renderContent = {}
        )
    }
}
