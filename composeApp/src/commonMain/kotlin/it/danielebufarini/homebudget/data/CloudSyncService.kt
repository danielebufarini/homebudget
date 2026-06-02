package it.danielebufarini.homebudget.data

class CloudSyncService(
    private val backupRestoreService: BackupRestoreService
) {
    suspend fun isRestoreTargetEmpty(): Boolean {
        return backupRestoreService.isRestoreTargetEmpty()
    }

    suspend fun buildBackupFile(): BudgetBackupFile {
        return backupRestoreService.buildBackupFile()
    }

    suspend fun previewRestore(jsonText: String): BudgetBackupCounters {
        return backupRestoreService.previewRestore(jsonText)
    }

    suspend fun restoreFromBackup(jsonText: String): BudgetBackupCounters {
        return backupRestoreService.restoreFromBackup(jsonText)
    }
}
