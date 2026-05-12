package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

class IosExpenseEditorSnapshot(
    val id: String,
    val amountInput: String,
    val dateMillis: Long,
    val categoryId: String,
    val descriptionText: String,
    val isShared: Boolean,
    val isRecurringMonthly: Boolean,
    val recurringSeriesId: String?
)

class IosExpenseEditorOperationResult(
    val isSuccess: Boolean,
    val errorKey: String? = null
)

class IosExpenseEditorController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        ensureIosExpenseEditorKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    fun loadExpense(
        id: String,
        onResult: (IosExpenseEditorSnapshot?) -> Unit
    ) {
        scope.launch {
            repository.insertDefaultCategoriesIfEmpty()
            val expense = repository.getExpenseById(id)
            onResult(
                expense?.let {
                    IosExpenseEditorSnapshot(
                        id = it.id,
                        amountInput = formatAmountInput(it.amount),
                        dateMillis = it.date,
                        categoryId = it.categoryId,
                        descriptionText = it.description.orEmpty(),
                        isShared = it.isShared == 1L,
                        isRecurringMonthly = it.recurringSeriesId != null,
                        recurringSeriesId = it.recurringSeriesId
                    )
                }
            )
        }
    }

    fun saveExpense(
        expenseId: String,
        amountInput: String,
        dateMillis: Long,
        categoryId: String,
        description: String,
        isShared: Boolean,
        isRecurringMonthly: Boolean,
        updateWholeSeries: Boolean,
        onComplete: (IosExpenseEditorOperationResult) -> Unit
    ) {
        val parsedAmount = parseAmountInput(amountInput)
        if (parsedAmount == null || parsedAmount.signum() <= 0) {
            onComplete(IosExpenseEditorOperationResult(false, "Enter a valid amount"))
            return
        }
        if (categoryId.isBlank()) {
            onComplete(IosExpenseEditorOperationResult(false, "Select Category"))
            return
        }
        if (dateMillis <= 0L) {
            onComplete(IosExpenseEditorOperationResult(false, "Select a date"))
            return
        }

        scope.launch {
            val result = runCatching {
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("expense-not-found")
                val normalizedDescription = description.ifBlank { null }
                val recurringSeriesId = existingExpense.recurringSeriesId

                when {
                    recurringSeriesId != null && updateWholeSeries -> {
                        repository.updateRecurringExpenseSeries(
                            anchorExpenseId = existingExpense.id,
                            seriesId = recurringSeriesId,
                            amount = parsedAmount,
                            date = dateMillis,
                            categoryId = categoryId,
                            description = normalizedDescription,
                            isShared = isShared
                        )
                    }

                    recurringSeriesId != null -> {
                        repository.insertExpenses(
                            listOf(
                                PendingExpense(
                                    id = existingExpense.id,
                                    amount = parsedAmount,
                                    date = dateMillis,
                                    categoryId = categoryId,
                                    description = normalizedDescription,
                                    isShared = isShared,
                                    recurringSeriesId = recurringSeriesId
                                )
                            )
                        )
                    }

                    isRecurringMonthly -> {
                        repository.insertExpenses(
                            buildRecurringMonthlyExpensesFromExistingExpense(
                                existingExpenseId = existingExpense.id,
                                amount = parsedAmount,
                                firstDate = dateMillis,
                                categoryId = categoryId,
                                description = description,
                                isShared = isShared,
                                recurringSeriesId = buildIosRecurringExpenseSeriesId(),
                                idProvider = ::buildIosExpenseId
                            )
                        )
                    }

                    else -> {
                        repository.insertExpenses(
                            listOf(
                                PendingExpense(
                                    id = existingExpense.id,
                                    amount = parsedAmount,
                                    date = dateMillis,
                                    categoryId = categoryId,
                                    description = normalizedDescription,
                                    isShared = isShared,
                                    recurringSeriesId = null
                                )
                            )
                        )
                    }
                }
            }

            onComplete(
                if (result.isSuccess) {
                    IosExpenseEditorOperationResult(true)
                } else {
                    IosExpenseEditorOperationResult(false, "Unable to save expense")
                }
            )
        }
    }

    fun deleteExpense(
        expenseId: String,
        deleteWholeSeries: Boolean,
        onComplete: (IosExpenseEditorOperationResult) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("expense-not-found")
                val recurringSeriesId = existingExpense.recurringSeriesId
                if (deleteWholeSeries && recurringSeriesId != null) {
                    repository.deleteRecurringExpenseSeries(recurringSeriesId)
                } else {
                    repository.deleteExpense(expenseId)
                }
            }

            onComplete(
                if (result.isSuccess) {
                    IosExpenseEditorOperationResult(true)
                } else {
                    IosExpenseEditorOperationResult(false, "Unable to delete expense")
                }
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

private fun ensureIosExpenseEditorKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}

private fun buildIosExpenseId(): String = IdGenerator.newId("expense")

private fun buildIosRecurringExpenseSeriesId(): String = IdGenerator.newId("recurring-expense")
