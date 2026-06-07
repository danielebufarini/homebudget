package it.danielebufarini.spesify.data

class BackupRestoreService(
    private val repository: ExpenseRepository
) {
    suspend fun isRestoreTargetEmpty(): Boolean {
        return repository.getAllExpensesSnapshot().isEmpty() &&
            repository.getAllIncomesSnapshot().isEmpty()
    }

    suspend fun buildBackupFile(): BudgetBackupFile {
        return exportBudgetBackup(repository)
    }

    suspend fun previewRestore(jsonText: String): BudgetBackupCounters {
        return parseBudgetBackup(jsonText)
    }

    suspend fun restoreFromBackup(jsonText: String): BudgetBackupCounters {
        return restoreBudgetBackup(repository, jsonText)
    }
}
