package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.amount
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.category
import homebudget.composeapp.generated.resources.close
import homebudget.composeapp.generated.resources.date
import homebudget.composeapp.generated.resources.delete_expense
import homebudget.composeapp.generated.resources.delete_recurring_expense_title
import homebudget.composeapp.generated.resources.description
import homebudget.composeapp.generated.resources.edit_expense
import homebudget.composeapp.generated.resources.enter_valid_amount
import homebudget.composeapp.generated.resources.expense_details
import homebudget.composeapp.generated.resources.installments
import homebudget.composeapp.generated.resources.options
import homebudget.composeapp.generated.resources.recurring_expense_action_delete
import homebudget.composeapp.generated.resources.recurring_expense_action_update
import homebudget.composeapp.generated.resources.recurring_expense_info
import homebudget.composeapp.generated.resources.recurring_expense_series_info
import homebudget.composeapp.generated.resources.recurring_monthly
import homebudget.composeapp.generated.resources.save_expense
import homebudget.composeapp.generated.resources.saving
import homebudget.composeapp.generated.resources.select_category
import homebudget.composeapp.generated.resources.select_date
import homebudget.composeapp.generated.resources.select_installments
import homebudget.composeapp.generated.resources.shared_expense
import homebudget.composeapp.generated.resources.single_payment
import homebudget.composeapp.generated.resources.unable_to_delete_expense
import homebudget.composeapp.generated.resources.unable_to_save_expense
import homebudget.composeapp.generated.resources.update_expense
import homebudget.composeapp.generated.resources.update_recurring_expense_title
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

internal data class PendingRecurringExpenseUpdate(
    val amount: Long,
    val date: Long,
    val categoryId: String,
    val description: String?,
    val isShared: Boolean,
)

internal enum class RecurringExpenseAction {
    Update,
    Delete,
}

internal data class AddExpenseRouteLabels(
    val addExpense: String,
    val addCategory: String,
    val add: String,
    val amount: String,
    val back: String,
    val cancel: String,
    val category: String,
    val close: String,
    val date: String,
    val deleteExpense: String,
    val deleteRecurringExpenseTitle: String,
    val description: String,
    val editExpense: String,
    val enterValidAmount: String,
    val expenseDetails: String,
    val installments: String,
    val options: String,
    val recurringExpenseActionDelete: String,
    val recurringExpenseActionUpdate: String,
    val recurringExpenseInfo: String,
    val recurringExpenseSeriesInfo: String,
    val recurringMonthly: String,
    val saveExpense: String,
    val saving: String,
    val selectCategory: String,
    val selectDate: String,
    val selectInstallments: String,
    val sharedExpense: String,
    val singlePayment: String,
    val unableToDeleteExpense: String,
    val unableToSaveExpense: String,
    val updateExpense: String,
    val updateRecurringExpenseTitle: String,
)

@Composable
internal fun addExpenseRouteLabels() = AddExpenseRouteLabels(
    addExpense = stringResource(Res.string.add_expense),
    addCategory = stringResource(Res.string.add_category),
    add = stringResource(Res.string.add),
    amount = stringResource(Res.string.amount),
    back = stringResource(Res.string.back),
    cancel = stringResource(Res.string.cancel),
    category = stringResource(Res.string.category),
    close = stringResource(Res.string.close),
    date = stringResource(Res.string.date),
    deleteExpense = stringResource(Res.string.delete_expense),
    deleteRecurringExpenseTitle = stringResource(Res.string.delete_recurring_expense_title),
    description = stringResource(Res.string.description),
    editExpense = stringResource(Res.string.edit_expense),
    enterValidAmount = stringResource(Res.string.enter_valid_amount),
    expenseDetails = stringResource(Res.string.expense_details),
    installments = stringResource(Res.string.installments),
    options = stringResource(Res.string.options),
    recurringExpenseActionDelete = stringResource(Res.string.recurring_expense_action_delete),
    recurringExpenseActionUpdate = stringResource(Res.string.recurring_expense_action_update),
    recurringExpenseInfo = stringResource(
        Res.string.recurring_expense_info,
        RECURRING_MONTHLY_OCCURRENCES / 12,
    ),
    recurringExpenseSeriesInfo = stringResource(Res.string.recurring_expense_series_info),
    recurringMonthly = stringResource(Res.string.recurring_monthly),
    saveExpense = stringResource(Res.string.save_expense),
    saving = stringResource(Res.string.saving),
    selectCategory = stringResource(Res.string.select_category),
    selectDate = stringResource(Res.string.select_date),
    selectInstallments = stringResource(Res.string.select_installments),
    sharedExpense = stringResource(Res.string.shared_expense),
    singlePayment = stringResource(Res.string.single_payment),
    unableToDeleteExpense = stringResource(Res.string.unable_to_delete_expense),
    unableToSaveExpense = stringResource(Res.string.unable_to_save_expense),
    updateExpense = stringResource(Res.string.update_expense),
    updateRecurringExpenseTitle = stringResource(Res.string.update_recurring_expense_title),
)

internal fun buildExpenseId(): String = IdGenerator.newId("expense")

internal fun buildRecurringExpenseSeriesId(): String = IdGenerator.newId("recurring-expense")

internal fun Long.formatExpenseDateLabel(): String {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    val day = date.day.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}
