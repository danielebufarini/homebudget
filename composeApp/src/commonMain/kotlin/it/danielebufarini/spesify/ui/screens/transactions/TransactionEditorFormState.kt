package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import it.danielebufarini.spesify.data.MAX_EXPENSE_INSTALLMENTS
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income

@Stable
internal class TransactionEditorFormState internal constructor(
    initialDateMillis: Long?,
    initiallyInitialized: Boolean,
) {
    var amount by mutableStateOf("")
    var description by mutableStateOf("")
    var selectedCategoryId by mutableStateOf<String?>(null)
    var selectedDateMillis by mutableStateOf(initialDateMillis)
    var installmentCount by mutableIntStateOf(1)
    var isRecurringMonthly by mutableStateOf(false)
    var recurringSeriesId by mutableStateOf<String?>(null)
    var isShared by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var showAddCategorySheet by mutableStateOf(false)
    var showCategoryPickerSheet by mutableStateOf(false)
    var isInitialized by mutableStateOf(initiallyInitialized)
        private set

    fun initializeFromExpense(expense: Expense) {
        amount = formatAmountInput(expense.amount)
        description = expense.description.orEmpty()
        selectedCategoryId = expense.categoryId
        selectedDateMillis = expense.date
        isRecurringMonthly = expense.recurringSeriesId != null
        recurringSeriesId = expense.recurringSeriesId
        isShared = expense.isShared == 1L
        isInitialized = true
    }

    fun initializeFromExpensePrefill(prefill: ExpenseEditorPrefill) {
        amount = formatAmountInput(prefill.amountMinor)
        description = prefill.description.orEmpty()
        selectedCategoryId = prefill.categoryId
        selectedDateMillis = prefill.dateMillis ?: selectedDateMillis
        installmentCount = 1
        isRecurringMonthly = false
        recurringSeriesId = null
        isShared = false
        isInitialized = true
    }

    fun initializeFromIncome(income: Income) {
        amount = formatAmountInput(income.amount)
        description = income.description.orEmpty()
        selectedCategoryId = income.categoryId
        selectedDateMillis = income.date
        recurringSeriesId = income.recurringSeriesId
        isInitialized = true
    }

    fun updateInstallmentCount(count: Int) {
        val normalized = count.coerceIn(1, MAX_EXPENSE_INSTALLMENTS)
        installmentCount = normalized
        if (normalized > 1) {
            isRecurringMonthly = false
        }
    }

    fun updateExpenseRecurringMonthly(enabled: Boolean) {
        isRecurringMonthly = enabled
        if (enabled) {
            installmentCount = 1
        }
    }
}

@Composable
internal fun rememberTransactionEditorFormState(
    resetKey: Any?,
    initialDateMillis: Long?,
    initiallyInitialized: Boolean,
): TransactionEditorFormState =
    remember(resetKey, initialDateMillis) {
        TransactionEditorFormState(
            initialDateMillis = initialDateMillis,
            initiallyInitialized = initiallyInitialized,
        )
    }
