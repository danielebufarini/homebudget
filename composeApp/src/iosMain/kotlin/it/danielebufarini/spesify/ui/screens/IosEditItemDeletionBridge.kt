package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

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
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

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
            runCatching {
                repository.deleteExpense(id)
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                repository.deleteRecurringExpenseSeries(seriesId)
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteIncome(id: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                repository.deleteIncome(id)
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String): IosBooleanResult {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                repository.deleteRecurringIncomeSeries(seriesId)
            }
        }
        return IosBooleanResult(result.isSuccess)
    }

}
