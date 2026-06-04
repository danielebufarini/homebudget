package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.IdGenerator
import it.danielebufarini.homebudget.data.PendingExpense
import it.danielebufarini.homebudget.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.danielebufarini.homebudget.data.evaluateAmountExpressionInput
import it.danielebufarini.homebudget.data.formatAmountInput
import it.danielebufarini.homebudget.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val repository: ExpenseRepository by lazy {
        ensureIosExpenseEditorKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    suspend fun loadExpense(id: String): IosExpenseEditorSnapshot? {
        val expense = withContext(Dispatchers.Default) {
            repository.seedStarterCategoriesIfEmpty()
            repository.getExpenseById(id)
        }
        return expense?.let {
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
    }

    suspend fun saveExpense(
        expenseId: String,
        amountInput: String,
        dateMillis: Long,
        categoryId: String,
        description: String,
        isShared: Boolean,
        isRecurringMonthly: Boolean,
        updateWholeSeries: Boolean
    ): IosExpenseEditorOperationResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosExpenseEditorOperationResult(false, "Enter a valid amount")
        }
        if (categoryId.isBlank()) {
            return IosExpenseEditorOperationResult(false, "Select Category")
        }
        if (dateMillis <= 0L) {
            return IosExpenseEditorOperationResult(false, "Select a date")
        }

        val result = withContext(Dispatchers.Default) {
            runCatching {
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
        }

        return if (result.isSuccess) {
            IosExpenseEditorOperationResult(true)
        } else {
            IosExpenseEditorOperationResult(false, "Unable to save expense")
        }
    }

    suspend fun deleteExpense(
        expenseId: String,
        deleteWholeSeries: Boolean
    ): IosExpenseEditorOperationResult {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("expense-not-found")
                val recurringSeriesId = existingExpense.recurringSeriesId
                if (deleteWholeSeries && recurringSeriesId != null) {
                    repository.deleteRecurringExpenseSeries(recurringSeriesId)
                } else {
                    repository.deleteExpense(expenseId)
                }
            }
        }

        return if (result.isSuccess) {
            IosExpenseEditorOperationResult(true)
        } else {
            IosExpenseEditorOperationResult(false, "Unable to delete expense")
        }
    }

}

private fun ensureIosExpenseEditorKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}

private fun buildIosExpenseId(): String = IdGenerator.newId("expense")

private fun buildIosRecurringExpenseSeriesId(): String = IdGenerator.newId("recurring-expense")
