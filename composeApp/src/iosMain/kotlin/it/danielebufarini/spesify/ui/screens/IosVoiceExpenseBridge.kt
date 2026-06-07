@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

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
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    suspend fun loadSnapshot(): IosVoiceExpenseSnapshot? {
        val result = withContext(Dispatchers.Default) {
            runCatching { loadIosVoiceExpenseSnapshot(repository) }
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
                repository = repository,
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
