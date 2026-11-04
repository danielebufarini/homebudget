package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.csv.exportBudgetItemsToCsv
import it.danielebufarini.spesify.di.iosKoin
import it.danielebufarini.spesify.localization.csvExportFailedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class IosCsvExportController {
    private val repository: ExpenseRepository by lazy {
        iosKoin().get<ExpenseRepository>()
    }

    suspend fun exportCsv(
        startDateMillis: Long,
        endDateMillis: Long
    ): IosTextExportResult {
        val fallbackMessage = csvExportFailedMessage()
        return withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val export = exportBudgetItemsToCsv(
                    repository = repository,
                    startDate = startDateMillis.toLocalDate(),
                    endDate = endDateMillis.toLocalDate()
                )
                IosTextExportResult(export.fileName, export.content, null)
            }.getOrElse { error ->
                IosTextExportResult(null, null, error.message ?: fallbackMessage)
            }
        }
    }

}

private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
