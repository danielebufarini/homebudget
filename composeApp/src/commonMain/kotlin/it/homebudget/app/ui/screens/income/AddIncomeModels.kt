package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.amount
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.category
import homebudget.composeapp.generated.resources.date
import homebudget.composeapp.generated.resources.delete_income
import homebudget.composeapp.generated.resources.delete_recurring_income_title
import homebudget.composeapp.generated.resources.description
import homebudget.composeapp.generated.resources.details
import homebudget.composeapp.generated.resources.edit_income
import homebudget.composeapp.generated.resources.enter_valid_amount
import homebudget.composeapp.generated.resources.options
import homebudget.composeapp.generated.resources.recurring_income_action_delete
import homebudget.composeapp.generated.resources.recurring_income_action_update
import homebudget.composeapp.generated.resources.recurring_income_info
import homebudget.composeapp.generated.resources.recurring_income_series_info
import homebudget.composeapp.generated.resources.recurring_monthly
import homebudget.composeapp.generated.resources.save
import homebudget.composeapp.generated.resources.select_category
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unable_to_delete_income
import homebudget.composeapp.generated.resources.unable_to_save_income
import homebudget.composeapp.generated.resources.update
import homebudget.composeapp.generated.resources.update_recurring_income_title
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

internal data class PendingRecurringIncomeUpdate(
    val amount: Long,
    val date: Long,
    val description: String?,
    val categoryId: String?,
)

internal enum class RecurringIncomeAction {
    Update,
    Delete,
}

internal data class AddIncomeRouteLabels(
    val addIncome: String,
    val addCategory: String,
    val add: String,
    val amount: String,
    val back: String,
    val cancel: String,
    val category: String,
    val date: String,
    val details: String,
    val deleteIncome: String,
    val deleteRecurringIncomeTitle: String,
    val description: String,
    val editIncome: String,
    val enterValidAmount: String,
    val options: String,
    val recurringIncomeActionDelete: String,
    val recurringIncomeActionUpdate: String,
    val recurringIncomeInfo: String,
    val recurringIncomeSeriesInfo: String,
    val recurringMonthly: String,
    val save: String,
    val selectCategory: String,
    val unableToDeleteIncome: String,
    val unableToSaveIncome: String,
    val update: String,
    val updateRecurringIncomeTitle: String,
)

@Composable
internal fun addIncomeRouteLabels() = AddIncomeRouteLabels(
    addIncome = stringResource(Res.string.add_income),
    addCategory = stringResource(Res.string.add_category),
    add = stringResource(Res.string.add),
    amount = stringResource(Res.string.amount),
    back = stringResource(Res.string.back),
    cancel = stringResource(Res.string.cancel),
    category = stringResource(Res.string.category),
    date = stringResource(Res.string.date),
    details = stringResource(Res.string.details),
    deleteIncome = stringResource(Res.string.delete_income),
    deleteRecurringIncomeTitle = stringResource(Res.string.delete_recurring_income_title),
    description = stringResource(Res.string.description),
    editIncome = stringResource(Res.string.edit_income),
    enterValidAmount = stringResource(Res.string.enter_valid_amount),
    options = stringResource(Res.string.options),
    recurringIncomeActionDelete = stringResource(Res.string.recurring_income_action_delete),
    recurringIncomeActionUpdate = stringResource(Res.string.recurring_income_action_update),
    recurringIncomeInfo = stringResource(
        Res.string.recurring_income_info,
        RECURRING_MONTHLY_OCCURRENCES / 12,
    ),
    recurringIncomeSeriesInfo = stringResource(Res.string.recurring_income_series_info),
    recurringMonthly = stringResource(Res.string.recurring_monthly),
    save = stringResource(Res.string.save),
    selectCategory = stringResource(Res.string.select_category),
    unableToDeleteIncome = stringResource(Res.string.unable_to_delete_income),
    unableToSaveIncome = stringResource(Res.string.unable_to_save_income),
    update = stringResource(Res.string.update),
    updateRecurringIncomeTitle = stringResource(Res.string.update_recurring_income_title),
)

internal fun buildIncomeId(): String = IdGenerator.newId("income")

internal fun buildRecurringIncomeSeriesId(): String = IdGenerator.newId("recurring-income")

internal fun buildInitialIncomeDateMillis(year: Int?, month: Int?): Long {
    val timeZone = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(timeZone).date
    val targetYear = year ?: now.year
    val targetMonth = month ?: (now.month.ordinal + 1)
    val dayOfMonth = now.day.coerceAtMost(daysInMonth(targetYear, targetMonth))
    return LocalDate(targetYear, targetMonth, dayOfMonth)
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
}

private fun daysInMonth(year: Int, month: Int) = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31
}

private fun isLeapYear(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

@Composable
internal fun Long.formatIncomeDateLabel(): String {
    val shortMonthNames = stringArrayResource(Res.array.short_month_names)
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.day.toString().padStart(2, '0')} ${shortMonthNames[date.month.ordinal]} ${date.year}"
}
