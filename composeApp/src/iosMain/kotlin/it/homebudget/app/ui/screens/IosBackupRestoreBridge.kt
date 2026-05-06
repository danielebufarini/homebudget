package it.homebudget.app.ui.screens

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_restore_failed
import homebudget.composeapp.generated.resources.backup_restore_invalid
import it.homebudget.app.data.BudgetBackupCounters
import it.homebudget.app.data.CloudSyncService
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatformTools

class IosBackupRestoreController {
    private val scope = MainScope()
    private val cloudSyncService: CloudSyncService by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CloudSyncService>()
    }

    fun prepareRestore(
        text: String,
        onComplete: (BudgetBackupCounters?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                cloudSyncService.previewRestore(text)
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: if (result.isSuccess) null else getString(Res.string.backup_restore_invalid)
            )
        }
    }

    fun isRestoreTargetEmpty(onComplete: (Boolean) -> Unit) {
        scope.launch {
            val isEmpty = runCatching {
                cloudSyncService.isRestoreTargetEmpty()
            }.getOrDefault(false)
            onComplete(isEmpty)
        }
    }

    fun restoreBackup(
        text: String,
        onComplete: (BudgetBackupCounters?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                cloudSyncService.restoreFromBackup(text)
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: if (result.isSuccess) null else getString(Res.string.backup_restore_failed)
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
