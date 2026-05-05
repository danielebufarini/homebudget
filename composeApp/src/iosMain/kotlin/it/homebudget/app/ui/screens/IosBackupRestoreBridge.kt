package it.homebudget.app.ui.screens

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_restore_failed
import homebudget.composeapp.generated.resources.backup_restore_invalid
import it.homebudget.app.data.*
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatformTools

class IosBackupRestoreController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun prepareRestore(
        text: String,
        onComplete: (BudgetBackupPreview?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                parseBudgetBackup(text)
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: if (result.isSuccess) null else getString(Res.string.backup_restore_invalid)
            )
        }
    }

    fun restoreBackup(
        text: String,
        onComplete: (BudgetBackupRestoreResult?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                restoreBudgetBackup(repository, text)
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
