package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

class IosVoiceExpenseSnapshot(
    val categories: List<IosVoiceExpenseCategory>,
    val recentExpenses: List<IosVoiceExpenseRecord>
)

class IosVoiceExpenseController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun loadSnapshot(
        onResult: (IosVoiceExpenseSnapshot?) -> Unit
    ) {
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { loadIosVoiceExpenseSnapshot(repository) }
            }

            onResult(result.getOrNull())
        }
    }

    fun createExpense(
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
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
            onComplete(result.first, result.second)
        }
    }

    fun updateExpense(
        expenseId: String,
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
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
            onComplete(result.first, result.second)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
