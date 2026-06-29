package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CategoryTotalRow
import it.danielebufarini.spesify.database.ExpenseMonthSummaryRow
import it.danielebufarini.spesify.database.HighestDaySummaryRow
import it.danielebufarini.spesify.database.MonthTotalRow
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.TopCategorySummaryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class DashboardRepository(
    database: SpesifyDatabase
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()

    fun getDashboardMonthSummary(
        year: Int,
        month: Int
    ): Flow<DashboardMonthSummary> {
        val yearMonth = yearMonthKey(year = year, month = month)

        return combine(
            expenseDao.getDashboardMonthSummaryAmountsForYearMonth(yearMonth),
            expenseDao.getDashboardCategoryAmountGroupsForYearMonth(yearMonth),
            expenseDao.getDashboardHighestDayForYearMonth(yearMonth)
        ) { summaryAmounts, categoryAmountGroups, highestDay ->
            val expenseAggregates = buildDashboardExpenseAggregates(
                summary = ExpenseMonthSummaryRow(
                    expenseCount = summaryAmounts.expenseCount,
                    totalAmount = summaryAmounts.totalAmount,
                    sharedAmount = summaryAmounts.sharedAmount
                ),
                categoryAmountGroups = categoryAmountGroups,
                highestDay = highestDay
            )
            buildDashboardMonthSummary(
                expenseSummary = expenseAggregates.summary,
                incomeAmount = summaryAmounts.incomeAmount,
                categoryTotals = expenseAggregates.categoryTotals,
                topCategory = expenseAggregates.topCategory,
                highestDay = expenseAggregates.highestDay
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getDashboardBalanceTrend(
        selectedYear: Int,
        selectedMonth: Int,
        trailingMonthCount: Int = 6
    ): Flow<DashboardBalanceTrend> {
        val selectedMonthKey = MonthKey(
            year = selectedYear,
            month = selectedMonth
        )
        val firstVisibleMonth = selectedMonthKey.minusMonths(trailingMonthCount - 1)
        val monthAfterLastVisibleMonth = selectedMonthKey.plusMonths(1)
        val fromInclusiveYearMonth = firstVisibleMonth.toYearMonthKey()
        val toExclusiveYearMonth = monthAfterLastVisibleMonth.toYearMonthKey()

        return combine(
            getExpenseTotalBeforeYearMonth(toExclusiveYearMonth = fromInclusiveYearMonth),
            getIncomeTotalBeforeYearMonth(toExclusiveYearMonth = fromInclusiveYearMonth),
            getMonthlyExpenseTotals(
                fromInclusiveYearMonth = fromInclusiveYearMonth,
                toExclusiveYearMonth = toExclusiveYearMonth
            ),
            getMonthlyIncomeTotals(
                fromInclusiveYearMonth = fromInclusiveYearMonth,
                toExclusiveYearMonth = toExclusiveYearMonth
            )
        ) { initialExpenseAmount, initialIncomeAmount, expenseTotals, incomeTotals ->
            DashboardBalanceTrend(
                initialExpenseAmount = initialExpenseAmount,
                initialIncomeAmount = initialIncomeAmount,
                expenseTotalsByMonth = expenseTotals.map { row ->
                    row.toDashboardMonthTotal()
                },
                incomeTotalsByMonth = incomeTotals.map { row ->
                    row.toDashboardMonthTotal()
                }
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getRecentTransactions(limit: Int): Flow<List<DashboardRecentTransaction>> {
        val toExclusiveMillis = currentMonthUpperBoundMillis()

        return expenseDao.getRecentTransactions(
            limit = limit,
            toExclusiveMillis = toExclusiveMillis
        ).map { rows ->
            rows.map { row ->
                DashboardRecentTransaction(
                    id = row.id,
                    type = DashboardRecentTransactionType.entries[row.typeOrdinal],
                    amount = row.amount,
                    date = row.date,
                    categoryId = row.categoryId,
                    description = row.description
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getRecurringExpenseOverviewForMonth(
        year: Int,
        month: Int
    ): Flow<RecurringExpenseOverview> {
        return expenseDao.getRecurringExpensesForYearMonth(
            yearMonth = yearMonthKey(year = year, month = month)
        )
            .map(::buildRecurringExpenseOverview)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    suspend fun getWidgetMonthSummary(
        year: Int,
        month: Int
    ): WidgetMonthSummary {
        val row = expenseDao.getWidgetMonthSummaryForYearMonth(
            yearMonth = yearMonthKey(year = year, month = month)
        )
        return WidgetMonthSummary(
            expenseAmount = row.expenseAmount,
            incomeAmount = row.incomeAmount
        )
    }

    private fun getMonthlyExpenseTotals(
        fromInclusiveYearMonth: Int,
        toExclusiveYearMonth: Int
    ): Flow<List<MonthTotalRow>> {
        return expenseDao.getDashboardExpenseMonthAmountGroupsBetweenYearMonths(
            fromInclusiveYearMonth = fromInclusiveYearMonth,
            toExclusiveYearMonth = toExclusiveYearMonth
        ).map { rows ->
            rows.toMonthTotals()
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun getExpenseTotalBeforeYearMonth(
        toExclusiveYearMonth: Int
    ): Flow<Long> {
        return expenseDao.getDashboardExpenseTotalBeforeYearMonth(
            toExclusiveYearMonth = toExclusiveYearMonth
        ).distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun getMonthlyIncomeTotals(
        fromInclusiveYearMonth: Int,
        toExclusiveYearMonth: Int
    ): Flow<List<MonthTotalRow>> {
        return incomeDao.getDashboardIncomeMonthAmountGroupsBetweenYearMonths(
            fromInclusiveYearMonth = fromInclusiveYearMonth,
            toExclusiveYearMonth = toExclusiveYearMonth
        ).map { rows ->
            rows.toMonthTotals()
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun getIncomeTotalBeforeYearMonth(
        toExclusiveYearMonth: Int
    ): Flow<Long> {
        return incomeDao.getDashboardIncomeTotalBeforeYearMonth(
            toExclusiveYearMonth = toExclusiveYearMonth
        ).distinctUntilChanged().flowOn(Dispatchers.Default)
    }

}

private fun currentMonthUpperBoundMillis(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    return MonthKey(
        year = today.year,
        month = today.month.number
    )
        .plusMonths(1)
        .toStartOfMonthMillis(timeZone)
}

private fun buildDashboardMonthSummary(
    expenseSummary: ExpenseMonthSummaryRow,
    incomeAmount: Long,
    categoryTotals: List<CategoryTotalRow>,
    topCategory: TopCategorySummaryRow?,
    highestDay: HighestDaySummaryRow?
): DashboardMonthSummary {
    val totalAmount = expenseSummary.totalAmount
    return DashboardMonthSummary(
        expenseCount = expenseSummary.expenseCount,
        totalAmount = totalAmount,
        incomeAmount = incomeAmount,
        sharedAmount = expenseSummary.sharedAmount,
        averageAmount = averageAmount(totalAmount, expenseSummary.expenseCount),
        topCategoryId = topCategory?.categoryId,
        highestDayOfMonth = highestDay?.dayOfMonth,
        highestDayAmount = highestDay?.amount ?: 0L,
        categoryTotals = categoryTotals.map { row ->
            DashboardCategoryTotal(
                categoryId = row.categoryId,
                amount = row.amount
            )
        }
    )
}

private fun MonthTotalRow.toDashboardMonthTotal(): DashboardMonthTotal {
    return DashboardMonthTotal(
        year = year,
        month = month,
        amount = amount
    )
}
