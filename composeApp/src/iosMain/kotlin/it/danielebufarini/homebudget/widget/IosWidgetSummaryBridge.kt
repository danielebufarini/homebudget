package it.danielebufarini.homebudget.widget

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.full_month_names
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.koin.mp.KoinPlatformTools
import kotlin.time.Clock

class IosWidgetSummary(
    val monthTitle: String,
    val expenseAmountText: String,
    val incomeAmountText: String,
    val updatedAtMillis: Long
)

class IosWidgetSummaryResult(
    val summary: IosWidgetSummary?,
    val errorMessage: String?
)

class IosWidgetSummaryController {
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    suspend fun loadCurrentMonthSummary(): IosWidgetSummaryResult {
        val monthNames = getStringArray(Res.array.full_month_names)
        val currencySymbol = getString(Res.string.currency_symbol)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val now = Clock.System.now()
                val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                val summary = repository.getWidgetMonthSummary(
                    year = localDate.year,
                    month = localDate.month.number
                )
                val monthName = monthNames.getOrElse(localDate.month.number - 1) {
                    localDate.month.name.lowercase().replaceFirstChar { it.titlecase() }
                }

                IosWidgetSummary(
                    monthTitle = "$monthName ${localDate.year}",
                    expenseAmountText = formatAmount(summary.expenseAmount, currencySymbol),
                    incomeAmountText = formatAmount(summary.incomeAmount, currencySymbol),
                    updatedAtMillis = now.toEpochMilliseconds()
                )
            }
        }
        return IosWidgetSummaryResult(
            summary = result.getOrNull(),
            errorMessage = result.exceptionOrNull()?.message
        )
    }

}
