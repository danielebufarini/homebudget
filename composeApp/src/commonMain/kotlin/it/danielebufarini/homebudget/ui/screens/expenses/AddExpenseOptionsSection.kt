package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import it.danielebufarini.homebudget.data.MAX_EXPENSE_INSTALLMENTS
import it.danielebufarini.homebudget.ui.screens.transactions.InstallmentsIcon
import it.danielebufarini.homebudget.ui.screens.transactions.RecurringIcon
import it.danielebufarini.homebudget.ui.screens.transactions.RecurringSeriesNotice
import it.danielebufarini.homebudget.ui.screens.transactions.SharedIcon
import it.danielebufarini.homebudget.ui.screens.transactions.SoftInlineInfoCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftSectionCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftToggleRow

@Composable
internal fun AddExpenseOptionsSection(
    labels: AddExpenseRouteLabels,
    readOnly: Boolean,
    expenseId: String?,
    installmentCount: Int,
    onInstallmentCountChange: (Int) -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
    isShared: Boolean,
    onSharedChange: (Boolean) -> Unit,
) {
    SoftSectionCard(title = labels.options) {
        if (expenseId == null && !isRecurringMonthly) {
            InstallmentRulerPicker(
                label = labels.installments,
                value = installmentCount,
                onValueChange = onInstallmentCountChange,
                enabled = !readOnly,
                valueRange = 1..MAX_EXPENSE_INSTALLMENTS,
                singlePaymentLabel = labels.singlePayment,
                installmentsLabel = labels.installments,
                icon = InstallmentsIcon,
            )
        }

        val showRecurringToggle = !readOnly && recurringSeriesId == null && installmentCount == 1
        AnimatedVisibility(
            visible = showRecurringToggle,
            enter = fadeIn(animationSpec = tween(180)) +
                expandVertically(animationSpec = tween(220)) +
                slideInVertically(animationSpec = tween(220)) { -it / 4 },
            exit = fadeOut(animationSpec = tween(120)) +
                shrinkVertically(animationSpec = tween(180)) +
                slideOutVertically(animationSpec = tween(180)) { -it / 4 },
        ) {
            Column {
                SoftToggleRow(
                    label = labels.recurringMonthly,
                    description = null,
                    icon = RecurringIcon,
                    checked = isRecurringMonthly,
                    onCheckedChange = onRecurringMonthlyChange,
                )
                SoftInlineInfoCard(
                    visible = isRecurringMonthly,
                    text = labels.recurringExpenseInfo,
                    icon = RecurringIcon,
                )
            }
        }

        if (recurringSeriesId != null) {
            RecurringSeriesNotice(text = labels.recurringExpenseSeriesInfo)
        }

        SoftToggleRow(
            label = labels.sharedExpense,
            description = null,
            icon = SharedIcon,
            checked = isShared,
            onCheckedChange = { if (!readOnly) onSharedChange(it) },
            enabled = !readOnly,
        )
    }
}

internal fun addExpenseConfirmLabel(
    labels: AddExpenseRouteLabels,
    isSaving: Boolean,
    expenseId: String?,
) = if (isSaving) {
    labels.saving
} else if (expenseId == null) {
    labels.saveExpense
} else {
    labels.updateExpense
}
