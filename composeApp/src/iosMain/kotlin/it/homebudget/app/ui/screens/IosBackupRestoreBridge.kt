package it.homebudget.app.ui.screens

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_restore_failed
import homebudget.composeapp.generated.resources.backup_restore_invalid
import it.homebudget.app.data.BudgetBackupCounters
import it.homebudget.app.data.CloudSyncService
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val fallbackMessage = getString(Res.string.backup_restore_invalid)
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    cloudSyncService.previewRestore(text)
                }
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: if (result.isSuccess) null else fallbackMessage
            )
        }
    }

    fun isRestoreTargetEmpty(onComplete: (Boolean) -> Unit) {
        scope.launch {
            val isEmpty = withContext(Dispatchers.Default) {
                runCatching {
                    cloudSyncService.isRestoreTargetEmpty()
                }.getOrDefault(false)
            }
            onComplete(isEmpty)
        }
    }

    fun restoreBackup(
        text: String,
        onComplete: (BudgetBackupCounters?, String?) -> Unit
    ) {
        scope.launch {
            val fallbackMessage = getString(Res.string.backup_restore_failed)
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    cloudSyncService.restoreFromBackup(text)
                }
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: if (result.isSuccess) null else fallbackMessage
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
