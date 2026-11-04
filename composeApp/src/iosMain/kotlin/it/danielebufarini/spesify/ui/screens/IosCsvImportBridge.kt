package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.csv.importBudgetItemsFromCsv
import it.danielebufarini.spesify.di.iosKoin
import it.danielebufarini.spesify.localization.csvImportFailedMessage
import it.danielebufarini.spesify.localization.csvImportNoRowsMessage
import it.danielebufarini.spesify.localization.csvImportSuccessMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IosMessageResult(
    val successMessage: String?,
    val errorMessage: String?
)

class IosCsvImportController {
    private val repository: ExpenseRepository by lazy {
        iosKoin().get<ExpenseRepository>()
    }

    suspend fun importCsv(text: String): IosMessageResult {
        val noRowsMessage = csvImportNoRowsMessage()
        val failedMessage = csvImportFailedMessage()
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val importResult = importBudgetItemsFromCsv(
                    repository = repository,
                    csvText = text
                )

                if (importResult.importedCount == 0 && importResult.skippedCount == 0) {
                    noRowsMessage
                } else {
                    csvImportSuccessMessage(
                        importedCount = importResult.importedCount,
                        skippedCount = importResult.skippedCount
                    )
                }
            }
        }
        return IosMessageResult(
            successMessage = result.getOrNull(),
            errorMessage = result.exceptionOrNull()?.message ?: failedMessage
        )
    }

}
