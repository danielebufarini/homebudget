package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.csv.importBudgetItemsFromCsv
import it.homebudget.app.di.initKoin
import it.homebudget.app.localization.csvImportFailedMessage
import it.homebudget.app.localization.csvImportNoRowsMessage
import it.homebudget.app.localization.csvImportSuccessMessage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

class IosCsvImportController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun importCsv(
        text: String,
        onComplete: (String?, String?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                val importResult = importBudgetItemsFromCsv(
                    repository = repository,
                    csvText = text
                )

                if (importResult.importedCount == 0 && importResult.skippedCount == 0) {
                    csvImportNoRowsMessage()
                } else {
                    csvImportSuccessMessage(
                        importedCount = importResult.importedCount,
                        skippedCount = importResult.skippedCount
                    )
                }
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: csvImportFailedMessage()
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
