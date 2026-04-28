package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun loadExpenseMetadata(
        id: String,
        onResult: (IosExpenseDeletionMetadata?) -> Unit
    ) {
        scope.launch {
            val expense = repository.getExpenseById(id)
            onResult(
                expense?.let {
                    IosExpenseDeletionMetadata(
                        id = it.id,
                        recurringSeriesId = it.recurringSeriesId
                    )
                }
            )
        }
    }

    fun loadIncomeMetadata(
        id: String,
        onResult: (IosIncomeDeletionMetadata?) -> Unit
    ) {
        scope.launch {
            val income = repository.getIncomeById(id)
            onResult(
                income?.let {
                    IosIncomeDeletionMetadata(
                        id = it.id,
                        recurringSeriesId = it.recurringSeriesId
                    )
                }
            )
        }
    }

    fun deleteExpense(
        id: String,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                repository.deleteExpense(id)
            }
            onComplete(result.isSuccess)
        }
    }

    fun deleteRecurringExpenseSeries(
        seriesId: String,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                repository.deleteRecurringExpenseSeries(seriesId)
            }
            onComplete(result.isSuccess)
        }
    }

    fun deleteIncome(
        id: String,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                repository.deleteIncome(id)
            }
            onComplete(result.isSuccess)
        }
    }

    fun deleteRecurringIncomeSeries(
        seriesId: String,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                repository.deleteRecurringIncomeSeries(seriesId)
            }
            onComplete(result.isSuccess)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
