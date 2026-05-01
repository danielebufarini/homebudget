package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.exportBudgetItemsToCsv
import it.homebudget.app.di.initKoin
import it.homebudget.app.localization.AppStrings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
            val result = runCatching {
                val export = exportBudgetItemsToCsv(
                    repository = repository,
                    startDate = startDateMillis.toLocalDate(),
                    endDate = endDateMillis.toLocalDate()
                )
                Triple(export.fileName, export.content, null as String?)
            }.getOrElse { error ->
                Triple(null, null, error.message ?: AppStrings.csvExportFailed)
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
