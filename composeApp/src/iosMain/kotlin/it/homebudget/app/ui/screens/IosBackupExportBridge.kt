package it.homebudget.app.ui.screens

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_export_failed
import it.homebudget.app.data.CloudSyncService
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatformTools

class IosBackupExportController {
    private val scope = MainScope()
    private val cloudSyncService: CloudSyncService by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CloudSyncService>()
    }

    fun exportBackup(
        onComplete: (String?, String?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                val backup = cloudSyncService.buildBackupFile()
                Triple(backup.fileName, backup.content, null as String?)
            }.getOrElse { error ->
                Triple(null, null, error.message ?: getString(Res.string.backup_export_failed))
            }

            onComplete(result.first, result.second, result.third)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
