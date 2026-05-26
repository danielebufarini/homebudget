package it.homebudget.app.data

import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.MonthTotalRow
import it.homebudget.app.database.TopCategorySummaryRow
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
import kotlin.time.Instant

class DashboardRepository(
    database: HomeBudgetDatabase
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()

    fun getDashboardMonthSummary(
        year: Int,
        month: Int
    ): Flow<DashboardMonthSummary> {
        val (startMillis, endMillis) = monthBounds(year, month)

        return combine(
            expenseDao.getExpenseCountBetween(startMillis, endMillis),
            expenseDao.getDashboardCategoryAmountGroupsBetween(startMillis, endMillis),
            expenseDao.getDashboardDayAmountGroupsBetween(startMillis, endMillis),
            expenseDao.getSharedExpenseAmountGroupBetween(startMillis, endMillis),
            incomeDao.getIncomeAmountGroupBetween(startMillis, endMillis)
        ) { expenseCount, categoryAmountGroups, dayAmountGroups, sharedAmountGroup, incomeAmountGroup ->
            val expenseAggregates = buildDashboardExpenseAggregates(
                expenseCount = expenseCount,
                categoryAmountGroups = categoryAmountGroups,
                dayAmountGroups = dayAmountGroups,
                sharedAmountGroup = sharedAmountGroup
            )
            val incomeAmount = incomeAmountGroup.totalAmount
            buildDashboardMonthSummary(
                expenseSummary = expenseAggregates.summary,
                incomeAmount = incomeAmount,
                categoryTotals = expenseAggregates.categoryTotals,
                topCategory = expenseAggregates.topCategory,
                highestDay = expenseAggregates.highestDay
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getDashboardCashFlow(
        selectedYear: Int,
        selectedMonth: Int,
        trailingMonthCount: Int = 6
    ): Flow<DashboardCashFlow> {
        val timeZone = TimeZone.currentSystemDefault()
        val selectedMonthKey = MonthKey(
            year = selectedYear,
            month = selectedMonth
        )
        val firstVisibleMonth = selectedMonthKey.minusMonths(trailingMonthCount - 1)
        val monthAfterLastVisibleMonth = selectedMonthKey.plusMonths(1)
        val fromInclusiveMillis = firstVisibleMonth.toStartOfMonthMillis(timeZone)
        val toExclusiveMillis = monthAfterLastVisibleMonth.toStartOfMonthMillis(timeZone)

        return combine(
            getMonthlyExpenseTotals(
                fromInclusiveMillis = fromInclusiveMillis,
                toExclusiveMillis = toExclusiveMillis
            ),
            getMonthlyIncomeTotals(
                fromInclusiveMillis = fromInclusiveMillis,
                toExclusiveMillis = toExclusiveMillis
            )
        ) { expenseTotals, incomeTotals ->
            DashboardCashFlow(
                expenseTotalsByMonth = expenseTotals.map { row ->
                    row.toDashboardMonthTotal(timeZone)
                },
                incomeTotalsByMonth = incomeTotals.map { row ->
                    row.toDashboardMonthTotal(timeZone)
                }
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getRecentTransactions(limit: Int): Flow<List<DashboardRecentTransaction>> {
        val toExclusiveMillis = currentMonthUpperBoundMillis()

        return combine(
            expenseDao.getRecentExpenses(
                limit = limit,
                toExclusiveMillis = toExclusiveMillis
            ),
            incomeDao.getRecentIncomes(
                limit = limit,
                toExclusiveMillis = toExclusiveMillis
            )
        ) { expenses, incomes ->
            buildList(expenses.size + incomes.size) {
                expenses.mapTo(this) { expense ->
                    DashboardRecentTransaction(
                        id = expense.id,
                        type = DashboardRecentTransactionType.Expense,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description
                    )
                }
                incomes.mapTo(this) { income ->
                    DashboardRecentTransaction(
                        id = income.id,
                        type = DashboardRecentTransactionType.Income,
                        amount = income.amount,
                        date = income.date,
                        categoryId = income.categoryId,
                        description = income.description
                    )
                }
            }.sortedWith(
                compareByDescending<DashboardRecentTransaction> { it.date }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.id }
            ).take(limit)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    suspend fun getWidgetMonthSummary(
        year: Int,
        month: Int
    ): WidgetMonthSummary {
        val (startMillis, endMillis) = monthBounds(year, month)
        val row = expenseDao.getWidgetMonthSummaryBetween(startMillis, endMillis)
        return WidgetMonthSummary(
            expenseAmount = row.expenseAmount,
            incomeAmount = row.incomeAmount
        )
    }

    private fun getMonthlyExpenseTotals(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<MonthTotalRow>> {
        return expenseDao.getDashboardExpenseMonthAmountGroupsBetween(
            fromInclusiveMillis = fromInclusiveMillis,
            toExclusiveMillis = toExclusiveMillis
        ).map { rows ->
            rows.toMonthTotals(timeZone = TimeZone.currentSystemDefault())
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun getMonthlyIncomeTotals(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<MonthTotalRow>> {
        return incomeDao.getDashboardIncomeMonthAmountGroupsBetween(
            fromInclusiveMillis = fromInclusiveMillis,
            toExclusiveMillis = toExclusiveMillis
        ).map { rows ->
            rows.toMonthTotals(timeZone = TimeZone.currentSystemDefault())
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
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

private fun MonthTotalRow.toDashboardMonthTotal(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): DashboardMonthTotal {
    val localDate = Instant.fromEpochMilliseconds(date)
        .toLocalDateTime(timeZone)
        .date
    return DashboardMonthTotal(
        year = localDate.year,
        month = localDate.month.number,
        amount = amount
    )
}
