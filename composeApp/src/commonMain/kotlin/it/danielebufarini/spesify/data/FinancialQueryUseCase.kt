package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.database.SpesifyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val STATUS_SUCCESS = "success"
private const val STATUS_FAILED = "failed"
private const val DASHBOARD_BALANCE_MONTH_COUNT = 6

enum class FinancialQueryAmountKind {
    Expenses,
    Income,
    Balance
}

data class FinancialQueryResult(
    val status: String,
    val amount: Long,
    val kind: FinancialQueryAmountKind,
    val message: String?
) {
    val isSuccess: Boolean get() = status == STATUS_SUCCESS

    companion object {
        fun success(
            amount: Long,
            kind: FinancialQueryAmountKind,
            message: String? = null
        ): FinancialQueryResult = FinancialQueryResult(
            status = STATUS_SUCCESS,
            amount = amount,
            kind = kind,
            message = message
        )

        fun failed(
            kind: FinancialQueryAmountKind,
            message: String
        ): FinancialQueryResult = FinancialQueryResult(
            status = STATUS_FAILED,
            amount = 0L,
            kind = kind,
            message = message
        )
    }
}

class FinancialQueryUseCase internal constructor(
    database: SpesifyDatabase,
    private val dashboardRepository: DashboardRepository
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()

    suspend fun getCurrentMonthExpensesTotal(): FinancialQueryResult {
        val currentMonth = currentMonthKey()
        return getExpensesTotalForMonth(
            year = currentMonth.year,
            month = currentMonth.month
        )
    }

    suspend fun getExpensesTotalForMonth(
        year: Int,
        month: Int
    ): FinancialQueryResult {
        val bounds = validatedMonthBounds(
            year = year,
            month = month
        ) ?: return FinancialQueryResult.failed(
            kind = FinancialQueryAmountKind.Expenses,
            message = "Please provide a valid month between 1 and 12."
        )
        return sumExpensesBetween(bounds.first, bounds.second)
    }

    suspend fun getExpensesTotalForPeriod(
        startDateMillis: Long,
        endDateMillis: Long
    ): FinancialQueryResult {
        val bounds = validatedInclusiveDateRangeBounds(
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis
        ) ?: return FinancialQueryResult.failed(
            kind = FinancialQueryAmountKind.Expenses,
            message = "Please provide a valid period where the end date is not before the start date."
        )
        return sumExpensesBetween(bounds.first, bounds.second)
    }

    suspend fun getCurrentMonthIncomeTotal(): FinancialQueryResult {
        val currentMonth = currentMonthKey()
        return getIncomeTotalForMonth(
            year = currentMonth.year,
            month = currentMonth.month
        )
    }

    suspend fun getIncomeTotalForMonth(
        year: Int,
        month: Int
    ): FinancialQueryResult {
        val bounds = validatedMonthBounds(
            year = year,
            month = month
        ) ?: return FinancialQueryResult.failed(
            kind = FinancialQueryAmountKind.Income,
            message = "Please provide a valid month between 1 and 12."
        )
        return sumIncomeBetween(bounds.first, bounds.second)
    }

    suspend fun getIncomeTotalForPeriod(
        startDateMillis: Long,
        endDateMillis: Long
    ): FinancialQueryResult {
        val bounds = validatedInclusiveDateRangeBounds(
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis
        ) ?: return FinancialQueryResult.failed(
            kind = FinancialQueryAmountKind.Income,
            message = "Please provide a valid period where the end date is not before the start date."
        )
        return sumIncomeBetween(bounds.first, bounds.second)
    }

    suspend fun getCurrentBalance(): FinancialQueryResult {
        return runCatching {
            val currentMonth = currentMonthKey()
            val balanceTrend = dashboardRepository.getDashboardBalanceTrend(
                selectedYear = currentMonth.year,
                selectedMonth = currentMonth.month,
                trailingMonthCount = DASHBOARD_BALANCE_MONTH_COUNT
            ).first()

            FinancialQueryResult.success(
                amount = balanceTrend.toDashboardBalanceFor(currentMonth),
                kind = FinancialQueryAmountKind.Balance
            )
        }.getOrElse { error ->
            FinancialQueryResult.failed(
                kind = FinancialQueryAmountKind.Balance,
                message = error.message ?: "Unable to read the current balance."
            )
        }
    }


    private fun DashboardBalanceTrend.toDashboardBalanceFor(
        selectedMonth: MonthKey
    ): Long {
        val months = List(DASHBOARD_BALANCE_MONTH_COUNT) { index ->
            selectedMonth.minusMonths(DASHBOARD_BALANCE_MONTH_COUNT - 1 - index)
        }
        val expenseAmountsByMonth = expenseTotalsByMonth.associate { total ->
            MonthKey(total.year, total.month) to total.amount
        }
        val incomeAmountsByMonth = incomeTotalsByMonth.associate { total ->
            MonthKey(total.year, total.month) to total.amount
        }

        var cumulativeExpenseAmount = initialExpenseAmount
        var cumulativeIncomeAmount = initialIncomeAmount
        months.forEach { month ->
            cumulativeExpenseAmount = addAmountsExact(
                cumulativeExpenseAmount,
                expenseAmountsByMonth[month] ?: 0L
            )
            cumulativeIncomeAmount = addAmountsExact(
                cumulativeIncomeAmount,
                incomeAmountsByMonth[month] ?: 0L
            )
        }
        return subtractAmountsExact(cumulativeIncomeAmount, cumulativeExpenseAmount)
    }

    private suspend fun sumExpensesBetween(
        startInclusiveMillis: Long,
        endExclusiveMillis: Long
    ): FinancialQueryResult {
        return runCatching {
            FinancialQueryResult.success(
                amount = expenseDao.getExpensesSnapshotBetween(
                    startMillis = startInclusiveMillis,
                    endMillis = endExclusiveMillis
                ).sumAmountOf(Expense::amount),
                kind = FinancialQueryAmountKind.Expenses
            )
        }.getOrElse { error ->
            FinancialQueryResult.failed(
                kind = FinancialQueryAmountKind.Expenses,
                message = error.message ?: "Unable to read the expense total."
            )
        }
    }

    private suspend fun sumIncomeBetween(
        startInclusiveMillis: Long,
        endExclusiveMillis: Long
    ): FinancialQueryResult {
        return runCatching {
            FinancialQueryResult.success(
                amount = incomeDao.getIncomesSnapshotBetween(
                    startMillis = startInclusiveMillis,
                    endMillis = endExclusiveMillis
                ).sumAmountOf(Income::amount),
                kind = FinancialQueryAmountKind.Income
            )
        }.getOrElse { error ->
            FinancialQueryResult.failed(
                kind = FinancialQueryAmountKind.Income,
                message = error.message ?: "Unable to read the income total."
            )
        }
    }

    private fun validatedMonthBounds(
        year: Int,
        month: Int
    ): Pair<Long, Long>? {
        if (month !in 1..12 || year !in 1..9999) return null
        return runCatching { monthBounds(year = year, month = month) }.getOrNull()
    }

    private fun validatedInclusiveDateRangeBounds(
        startDateMillis: Long,
        endDateMillis: Long
    ): Pair<Long, Long>? {
        if (startDateMillis <= 0L || endDateMillis <= 0L) return null
        val timeZone = TimeZone.currentSystemDefault()
        val startDate = runCatching {
            Instant.fromEpochMilliseconds(startDateMillis)
                .toLocalDateTime(timeZone)
                .date
        }.getOrNull() ?: return null
        val endDate = runCatching {
            Instant.fromEpochMilliseconds(endDateMillis)
                .toLocalDateTime(timeZone)
                .date
        }.getOrNull() ?: return null
        if (endDate < startDate) return null
        val startInclusive = startDate
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()
        val endExclusive = endDate
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()
        return startInclusive to endExclusive
    }

    private fun currentMonthKey(
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): MonthKey {
        val today = Clock.System.now().toLocalDateTime(timeZone).date
        return MonthKey(
            year = today.year,
            month = today.month.number
        )
    }
}
