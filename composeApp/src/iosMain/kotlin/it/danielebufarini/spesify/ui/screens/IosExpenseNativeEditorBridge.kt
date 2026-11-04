package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.DeleteTransactionCommand
import it.danielebufarini.spesify.data.EditorDeletion
import it.danielebufarini.spesify.data.EditorItem
import it.danielebufarini.spesify.data.EditorMutationScope
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.ExpenseSchedule
import it.danielebufarini.spesify.data.SaveExpenseCommand
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.data.TransactionKind
import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.data.requireSuccess
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        iosKoin().get()
    }

    private val editorService: TransactionEditorService by lazy { iosKoin().get() }

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
            runCatchingCancellable {
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("expense-not-found")
                editorService.execute(SaveExpenseCommand(
                    item = EditorItem.Existing(existingExpense.id, existingExpense.recurringSeriesId),
                    amount = parsedAmount, date = dateMillis, categoryId = categoryId,
                    description = description.ifBlank { null }, isShared = isShared,
                    schedule = if (isRecurringMonthly) ExpenseSchedule.Monthly else ExpenseSchedule.Single,
                    scope = if (updateWholeSeries) EditorMutationScope.Series else EditorMutationScope.Single
                )).requireSuccess()
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
            runCatchingCancellable {
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("expense-not-found")
                val target = if (deleteWholeSeries && existingExpense.recurringSeriesId != null) {
                    EditorDeletion.Series(existingExpense.recurringSeriesId)
                } else EditorDeletion.Single(expenseId)
                editorService.execute(DeleteTransactionCommand(TransactionKind.Expense, target)).requireSuccess()
            }
        }

        return if (result.isSuccess) {
            IosExpenseEditorOperationResult(true)
        } else {
            IosExpenseEditorOperationResult(false, "Unable to delete expense")
        }
    }

}
