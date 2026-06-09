package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.FinancialQueryAmountKind
import it.danielebufarini.spesify.data.FinancialQueryResult
import it.danielebufarini.spesify.data.FinancialQueryUseCase
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosFinancialQueryIntentResult(
    val status: String,
    val amount: Long,
    val kind: String,
    val message: String?
) {
    val isSuccess: Boolean get() = status == STATUS_SUCCESS

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

class IosFinancialQueryIntentController {
    private val financialQueryUseCase: FinancialQueryUseCase by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<FinancialQueryUseCase>()
    }

    suspend fun getCurrentMonthExpensesTotal(): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getCurrentMonthExpensesTotal()
    }

    suspend fun getExpensesTotalForMonth(
        year: Int,
        month: Int
    ): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getExpensesTotalForMonth(
            year = year,
            month = month
        )
    }

    suspend fun getExpensesTotalForPeriod(
        startDateMillis: Long,
        endDateMillis: Long
    ): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getExpensesTotalForPeriod(
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis
        )
    }

    suspend fun getCurrentMonthIncomeTotal(): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getCurrentMonthIncomeTotal()
    }

    suspend fun getIncomeTotalForMonth(
        year: Int,
        month: Int
    ): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getIncomeTotalForMonth(
            year = year,
            month = month
        )
    }

    suspend fun getIncomeTotalForPeriod(
        startDateMillis: Long,
        endDateMillis: Long
    ): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getIncomeTotalForPeriod(
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis
        )
    }

    suspend fun getCurrentBalance(): IosFinancialQueryIntentResult = runQuery {
        financialQueryUseCase.getCurrentBalance()
    }

    private suspend fun runQuery(
        block: suspend () -> FinancialQueryResult
    ): IosFinancialQueryIntentResult {
        return withContext(Dispatchers.Default) {
            block()
        }.toIosIntentResult()
    }
}

private fun FinancialQueryResult.toIosIntentResult(): IosFinancialQueryIntentResult {
    return IosFinancialQueryIntentResult(
        status = status,
        amount = amount,
        kind = kind.toExternalValue(),
        message = message
    )
}

private fun FinancialQueryAmountKind.toExternalValue(): String {
    return when (this) {
        FinancialQueryAmountKind.Expenses -> "expenses"
        FinancialQueryAmountKind.Income -> "income"
        FinancialQueryAmountKind.Balance -> "balance"
    }
}
