package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.csv.exportBudgetItemsToCsv
import it.danielebufarini.homebudget.di.initKoin
import it.danielebufarini.homebudget.localization.csvExportFailedMessage
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
