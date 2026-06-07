package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.CloudSyncService
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatformTools
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.backup_export_failed

class IosTextExportResult(
    val fileName: String?,
    val content: String?,
    val errorMessage: String?
)

class IosBackupExportController {
    private val cloudSyncService: CloudSyncService by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CloudSyncService>()
    }

    suspend fun exportBackup(): IosTextExportResult {
        val fallbackMessage = getString(Res.string.backup_export_failed)
        return withContext(Dispatchers.Default) {
            runCatching {
                val backup = cloudSyncService.buildBackupFile()
                IosTextExportResult(backup.fileName, backup.content, null)
            }.getOrElse { error ->
                IosTextExportResult(null, null, error.message ?: fallbackMessage)
            }
        }
    }

}
