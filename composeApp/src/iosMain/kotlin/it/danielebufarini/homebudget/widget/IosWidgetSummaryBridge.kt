package it.danielebufarini.homebudget.widget

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.full_month_names
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

class IosWidgetSummaryController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun loadCurrentMonthSummary(onComplete: (IosWidgetSummary?, String?) -> Unit) {
        scope.launch {
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

            result
                .onSuccess { summary -> onComplete(summary, null) }
                .onFailure { error -> onComplete(null, error.message) }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
