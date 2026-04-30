package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.importExpensesFromCsv
import it.homebudget.app.di.initKoin
import it.homebudget.app.localization.AppStrings
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
                val importResult = importExpensesFromCsv(
                    repository = repository,
                    csvText = text
                )

                if (importResult.importedCount == 0 && importResult.skippedCount == 0) {
                    AppStrings.csvImportNoRows
                } else {
                    AppStrings.csvImportSuccess(
                        importedCount = importResult.importedCount,
                        skippedCount = importResult.skippedCount
                    )
                }
            }

            onComplete(
                result.getOrNull(),
                result.exceptionOrNull()?.message ?: AppStrings.csvImportFailed
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
