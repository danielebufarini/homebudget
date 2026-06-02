package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.csv.exportBudgetItemsToCsv
import it.danielebufarini.homebudget.di.initKoin
import it.danielebufarini.homebudget.localization.csvExportFailedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatformTools
import kotlin.time.Instant

class IosCsvExportController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun exportCsv(
        startDateMillis: Long,
        endDateMillis: Long,
        onComplete: (String?, String?, String?) -> Unit
    ) {
        scope.launch {
            val fallbackMessage = csvExportFailedMessage()
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val export = exportBudgetItemsToCsv(
                        repository = repository,
                        startDate = startDateMillis.toLocalDate(),
                        endDate = endDateMillis.toLocalDate()
                    )
                    Triple(export.fileName, export.content, null as String?)
                }.getOrElse { error ->
                    Triple(null, null, error.message ?: fallbackMessage)
                }
            }

            onComplete(result.first, result.second, result.third)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
