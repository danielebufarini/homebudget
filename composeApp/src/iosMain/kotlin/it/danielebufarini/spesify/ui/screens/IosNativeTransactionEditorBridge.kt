package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.DeleteTransactionCommand
import it.danielebufarini.spesify.data.EditorDeletion
import it.danielebufarini.spesify.data.EditorItem
import it.danielebufarini.spesify.data.EditorMutationScope
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.ExpenseSchedule
import it.danielebufarini.spesify.data.SaveExpenseCommand
import it.danielebufarini.spesify.data.SaveIncomeCommand
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.data.TransactionKind
import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.data.requireSuccess
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IosNativeTransactionEditorResult(
    val isSuccess: Boolean,
    val errorKey: String? = null
)

class IosNativeIncomeEditorSnapshot(
    val id: String,
    val amountInput: String,
    val dateMillis: Long,
    val categoryId: String?,
    val descriptionText: String,
    val isRecurringMonthly: Boolean,
    val recurringSeriesId: String?
)

class IosNativeTransactionEditorController {
    private val repository: ExpenseRepository by lazy {
        iosKoin().get()
    }

    private val editorService: TransactionEditorService by lazy { iosKoin().get() }

    suspend fun loadIncome(id: String): IosNativeIncomeEditorSnapshot? {
        val income = withContext(Dispatchers.Default) {
            repository.getIncomeById(id)
        }
        return income?.let {
            IosNativeIncomeEditorSnapshot(
                id = it.id,
                amountInput = formatAmountInput(it.amount),
                dateMillis = it.date,
                categoryId = it.categoryId,
                descriptionText = it.description.orEmpty(),
                isRecurringMonthly = it.recurringSeriesId != null,
                recurringSeriesId = it.recurringSeriesId
            )
        }
    }

    suspend fun saveExpense(
        amountInput: String,
        dateMillis: Long,
        categoryId: String,
        description: String,
        isShared: Boolean,
        isRecurringMonthly: Boolean,
        installmentCount: Int
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (categoryId.isBlank()) {
            return IosNativeTransactionEditorResult(false, "Select Category")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(SaveExpenseCommand(
                    item = EditorItem.New(), amount = parsedAmount, date = dateMillis, categoryId = categoryId,
                    description = description.ifBlank { null }, isShared = isShared,
                    schedule = if (isRecurringMonthly) ExpenseSchedule.Monthly else ExpenseSchedule.Installments(installmentCount)
                )).requireSuccess()
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save expense.")
        }
    }

    suspend fun saveIncome(
        amountInput: String,
        dateMillis: Long,
        categoryId: String?,
        description: String,
        isRecurringMonthly: Boolean
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val normalizedCategoryId = categoryId?.takeIf { it.isNotBlank() }
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(SaveIncomeCommand(
                    item = EditorItem.New(), amount = parsedAmount, date = dateMillis, categoryId = normalizedCategoryId,
                    description = description.ifBlank { null }, recurringMonthly = isRecurringMonthly
                )).requireSuccess()
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save income.")
        }
    }

    suspend fun saveExistingIncome(
        incomeId: String,
        amountInput: String,
        dateMillis: Long,
        categoryId: String?,
        description: String,
        isRecurringMonthly: Boolean,
        updateWholeSeries: Boolean
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val normalizedCategoryId = categoryId?.takeIf { it.isNotBlank() }
        val normalizedDescription = description.ifBlank { null }
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val existingIncome = repository.getIncomeById(incomeId)
                    ?: error("income-not-found")
                editorService.execute(SaveIncomeCommand(
                    item = EditorItem.Existing(existingIncome.id, existingIncome.recurringSeriesId),
                    amount = parsedAmount, date = dateMillis, categoryId = normalizedCategoryId,
                    description = normalizedDescription, recurringMonthly = isRecurringMonthly,
                    scope = if (updateWholeSeries) EditorMutationScope.Series else EditorMutationScope.Single
                )).requireSuccess()
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save income.")
        }
    }

    suspend fun deleteIncome(
        incomeId: String,
        deleteWholeSeries: Boolean
    ): IosNativeTransactionEditorResult {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val existingIncome = repository.getIncomeById(incomeId)
                    ?: error("income-not-found")
                val target = if (deleteWholeSeries && existingIncome.recurringSeriesId != null) {
                    EditorDeletion.Series(existingIncome.recurringSeriesId)
                } else EditorDeletion.Single(incomeId)
                editorService.execute(DeleteTransactionCommand(TransactionKind.Income, target)).requireSuccess()
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to delete income.")
        }
    }

}
