package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.BudgetBackupCounters
import it.danielebufarini.spesify.data.CloudSyncService
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatformTools
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.backup_restore_failed
import spesify.composeapp.generated.resources.backup_restore_invalid

class IosBackupRestoreResult(
    val counters: BudgetBackupCounters?,
    val errorMessage: String?
)

class IosBackupRestoreController {
    private val cloudSyncService: CloudSyncService by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CloudSyncService>()
    }

    suspend fun prepareRestore(text: String): IosBackupRestoreResult {
        val fallbackMessage = getString(Res.string.backup_restore_invalid)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                cloudSyncService.previewRestore(text)
            }
        }
        return IosBackupRestoreResult(
            counters = result.getOrNull(),
            errorMessage = result.exceptionOrNull()?.message ?: if (result.isSuccess) null else fallbackMessage
        )
    }

    suspend fun isRestoreTargetEmpty(): IosBooleanResult {
        val isEmpty = withContext(Dispatchers.Default) {
            runCatching {
                cloudSyncService.isRestoreTargetEmpty()
            }.getOrDefault(false)
        }
        return IosBooleanResult(isEmpty)
    }

    suspend fun restoreBackup(text: String): IosBackupRestoreResult {
        val fallbackMessage = getString(Res.string.backup_restore_failed)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                cloudSyncService.restoreFromBackup(text)
            }
        }
        return IosBackupRestoreResult(
            counters = result.getOrNull(),
            errorMessage = result.exceptionOrNull()?.message ?: if (result.isSuccess) null else fallbackMessage
        )
    }

}
