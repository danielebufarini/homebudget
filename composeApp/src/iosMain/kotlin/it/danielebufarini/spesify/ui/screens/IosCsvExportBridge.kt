package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.csv.exportBudgetItemsToCsv
import it.danielebufarini.spesify.di.initKoin
import it.danielebufarini.spesify.localization.csvExportFailedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatformTools
import kotlin.time.Instant

class IosCsvExportController {
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    suspend fun exportCsv(
        startDateMillis: Long,
        endDateMillis: Long
    ): IosTextExportResult {
        val fallbackMessage = csvExportFailedMessage()
        return withContext(Dispatchers.Default) {
            runCatching {
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
