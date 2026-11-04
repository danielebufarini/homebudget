@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IosVoiceExpenseCategory(
    val id: String,
    val name: String
)

class IosVoiceExpenseRecord(
    val id: String,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    @property:ObjCName(swiftName = "expenseDescription")
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

class IosVoiceExpenseSnapshot(
    val categories: List<IosVoiceExpenseCategory>,
    val recentExpenses: List<IosVoiceExpenseRecord>
)

class IosVoiceExpensePersistResult(
    val isSuccess: Boolean,
    val message: String?
)

class IosVoiceExpenseController {
    private val editorService: TransactionEditorService by lazy { iosKoin().get() }
    private val repository: ExpenseRepository by lazy {
        iosKoin().get<ExpenseRepository>()
    }

    suspend fun loadSnapshot(): IosVoiceExpenseSnapshot? {
        val result = withContext(Dispatchers.Default) {
            runCatchingCancellable { loadIosVoiceExpenseSnapshot(repository) }
        }
        return result.getOrNull()
    }

    suspend fun createExpense(
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean
    ): IosVoiceExpensePersistResult {
        val result = withContext(Dispatchers.Default) {
            createIosVoiceExpense(
                editorService = editorService,
                amountInput = amountInput,
                categoryId = categoryId,
                description = description,
                date = date,
                isShared = isShared
            )
        }
        return IosVoiceExpensePersistResult(result.first, result.second)
    }

    suspend fun updateExpense(
        expenseId: String,
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean
    ): IosVoiceExpensePersistResult {
        val result = withContext(Dispatchers.Default) {
            updateIosVoiceExpense(
                repository = repository,
                editorService = editorService,
                expenseId = expenseId,
                amountInput = amountInput,
                categoryId = categoryId,
                description = description,
                date = date,
                isShared = isShared
            )
        }
        return IosVoiceExpensePersistResult(result.first, result.second)
    }

}
