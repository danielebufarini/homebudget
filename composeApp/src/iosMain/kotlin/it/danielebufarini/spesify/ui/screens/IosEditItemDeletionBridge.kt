package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.data.DeleteTransactionCommand
import it.danielebufarini.spesify.data.TransactionKind
import it.danielebufarini.spesify.data.EditorDeletion
import it.danielebufarini.spesify.data.requireSuccess
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IosExpenseDeletionMetadata(
    val id: String,
    val recurringSeriesId: String?
)

class IosIncomeDeletionMetadata(
    val id: String,
    val recurringSeriesId: String?
)

class IosEditItemDeletionController {
    private val repository: ExpenseRepository by lazy {
        iosKoin().get<ExpenseRepository>()
    }

    private val editorService: TransactionEditorService by lazy { iosKoin().get() }

    suspend fun loadExpenseMetadata(id: String): IosExpenseDeletionMetadata? {
        val expense = withContext(Dispatchers.Default) {
            repository.getExpenseById(id)
        }
        return expense?.let {
            IosExpenseDeletionMetadata(
                id = it.id,
                recurringSeriesId = it.recurringSeriesId
            )
        }
    }

    suspend fun loadIncomeMetadata(id: String): IosIncomeDeletionMetadata? {
        val income = withContext(Dispatchers.Default) {
            repository.getIncomeById(id)
        }
        return income?.let {
            IosIncomeDeletionMetadata(
                id = it.id,
                recurringSeriesId = it.recurringSeriesId
            )
        }
    }

    suspend fun deleteExpense(id: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Single(id))).requireSuccess()
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Series(seriesId))).requireSuccess()
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteIncome(id: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Single(id))).requireSuccess()
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable {
                editorService.execute(DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Series(seriesId))).requireSuccess()
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

}
