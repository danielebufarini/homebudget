package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.buildPendingExpenses
import it.homebudget.app.data.buildRecurringMonthlyExpenses
import it.homebudget.app.data.buildRecurringMonthlyIncomes
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

class IosNativeTransactionEditorResult(
    val isSuccess: Boolean,
    val errorKey: String? = null
)

class IosNativeTransactionEditorController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        ensureIosNativeTransactionEditorKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    fun saveExpense(
        amountInput: String,
        dateMillis: Long,
        categoryId: String,
        description: String,
        isShared: Boolean,
        isRecurringMonthly: Boolean,
        installmentCount: Int,
        onComplete: (IosNativeTransactionEditorResult) -> Unit
    ) {
        val parsedAmount = parseAmountInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            onComplete(IosNativeTransactionEditorResult(false, "Enter a valid amount"))
            return
        }
        if (categoryId.isBlank()) {
            onComplete(IosNativeTransactionEditorResult(false, "Select Category"))
            return
        }
        if (dateMillis <= 0L) {
            onComplete(IosNativeTransactionEditorResult(false, "Select a date"))
            return
        }

        scope.launch {
            val result = runCatching {
                val expenses = if (isRecurringMonthly) {
                    buildRecurringMonthlyExpenses(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        categoryId = categoryId,
                        description = description,
                        isShared = isShared,
                        recurringSeriesId = buildIosRecurringExpenseSeriesId(),
                        idProvider = ::buildIosExpenseId
                    )
                } else {
                    buildPendingExpenses(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        installments = installmentCount,
                        categoryId = categoryId,
                        description = description,
                        isShared = isShared,
                        idProvider = ::buildIosExpenseId
                    )
                }
                repository.insertExpenses(expenses)
            }

            onComplete(
                if (result.isSuccess) {
                    IosNativeTransactionEditorResult(true)
                } else {
                    IosNativeTransactionEditorResult(false, "Unable to save expense.")
                }
            )
        }
    }

    fun saveIncome(
        amountInput: String,
        dateMillis: Long,
        categoryId: String?,
        description: String,
        isRecurringMonthly: Boolean,
        onComplete: (IosNativeTransactionEditorResult) -> Unit
    ) {
        val parsedAmount = parseAmountInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            onComplete(IosNativeTransactionEditorResult(false, "Enter a valid amount"))
            return
        }
        if (dateMillis <= 0L) {
            onComplete(IosNativeTransactionEditorResult(false, "Select a date"))
            return
        }

        scope.launch {
            val normalizedCategoryId = categoryId?.takeIf { it.isNotBlank() }
            val result = runCatching {
                val incomes = if (isRecurringMonthly) {
                    buildRecurringMonthlyIncomes(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        description = description,
                        categoryId = normalizedCategoryId,
                        recurringSeriesId = buildIosRecurringIncomeSeriesId(),
                        idProvider = ::buildIosIncomeId
                    )
                } else {
                    listOf(
                        PendingIncome(
                            id = buildIosIncomeId(),
                            amount = parsedAmount,
                            date = dateMillis,
                            description = description.ifBlank { null },
                            categoryId = normalizedCategoryId,
                            recurringSeriesId = null
                        )
                    )
                }
                repository.insertIncomes(incomes)
            }

            onComplete(
                if (result.isSuccess) {
                    IosNativeTransactionEditorResult(true)
                } else {
                    IosNativeTransactionEditorResult(false, "Unable to save income.")
                }
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

private fun ensureIosNativeTransactionEditorKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}

private fun buildIosExpenseId(): String = IdGenerator.newId("expense")

private fun buildIosIncomeId(): String = IdGenerator.newId("income")

private fun buildIosRecurringExpenseSeriesId(): String = IdGenerator.newId("recurring-expense")

private fun buildIosRecurringIncomeSeriesId(): String = IdGenerator.newId("recurring-income")
