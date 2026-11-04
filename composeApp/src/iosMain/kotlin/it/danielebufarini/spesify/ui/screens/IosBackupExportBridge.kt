package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.CloudSyncService
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.backup_export_failed

class IosTextExportResult(
    val fileName: String?,
    val content: String?,
    val errorMessage: String?
)

class IosBackupExportController {
    private val cloudSyncService: CloudSyncService by lazy {
        iosKoin().get<CloudSyncService>()
    }

    suspend fun exportBackup(): IosTextExportResult {
        val fallbackMessage = getString(Res.string.backup_export_failed)
        return withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val backup = cloudSyncService.buildBackupFile()
                IosTextExportResult(backup.fileName, backup.content, null)
            }.getOrElse { error ->
                IosTextExportResult(null, null, error.message ?: fallbackMessage)
            }
        }
    }

}
