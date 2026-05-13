package it.homebudget.app.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.database.Category
import it.homebudget.app.database.CategoryTotalRow
import it.homebudget.app.database.Expense
import it.homebudget.app.database.ExpenseMonthSummaryRow
import it.homebudget.app.database.HighestDaySummaryRow
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Income
import it.homebudget.app.database.MonthTotalRow
import it.homebudget.app.database.TopCategorySummaryRow
import it.homebudget.app.widget.HomeBudgetWidgetRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private data class DefaultCategorySeed(
    val name: String,
    val icon: String
)

data class RestoredCategory(
    val id: String,
    val name: String,
    val icon: String,
    val isCustom: Boolean
)

data class DashboardMonthTotal(
    val year: Int,
    val month: Int,
    val amount: BigInteger
)

data class DashboardCategoryTotal(
    val categoryId: String,
    val amount: BigInteger
)

data class DashboardMonthSummary(
    val expenseCount: Int,
    val totalAmount: BigInteger,
    val incomeAmount: BigInteger,
    val sharedAmount: BigInteger,
    val averageAmount: BigInteger,
    val topCategoryId: String?,
    val highestDayOfMonth: Int?,
    val highestDayAmount: BigInteger,
    val categoryTotals: List<DashboardCategoryTotal>
)

data class DashboardCashFlow(
    val expenseTotalsByMonth: List<DashboardMonthTotal>,
    val incomeTotalsByMonth: List<DashboardMonthTotal>
)

class ExpenseRepository(private val database: HomeBudgetDatabase) {

    private val expenseDao = database.expenseDao()
    private val categoryDao = database.categoryDao()
    private val incomeDao = database.incomeDao()

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories().distinctUntilChanged()

    suspend fun insertCategory(id: String, name: String, icon: String, isCustom: Boolean) {
        categoryDao.insertCategory(
            Category(
                id = id,
                name = name,
                icon = icon,
                isCustom = if (isCustom) 1L else 0L
            )
        )
    }

    suspend fun updateCategory(id: String, name: String, icon: String) {
        categoryDao.updateCategory(id = id, name = name, icon = icon)
    }

    suspend fun insertDefaultCategoriesIfEmpty() {
        writeTransaction {
            if (categoryDao.countCategories() == 0L) {
                val defaults = listOf(
                    DefaultCategorySeed("Household", "home"),
                    DefaultCategorySeed("Food", "shopping_cart"),
                    DefaultCategorySeed("Restaurant", "restaurant"),
                    DefaultCategorySeed("Car", "directions_car"),
                    DefaultCategorySeed("Travel", "flight"),
                    DefaultCategorySeed("Healthcare", "local_hospital"),
                    DefaultCategorySeed("Personal", "person"),
                    DefaultCategorySeed("Other", "category")
                )
                categoryDao.insertCategories(
                    defaults.mapIndexed { index, category ->
                        Category(
                            id = "default_$index",
                            name = category.name,
                            icon = category.icon,
                            isCustom = 0L
                        )
                    }
                )
            } else {
                normalizeDefaultCategories()
            }
        }
    }

    private suspend fun normalizeDefaultCategories() {
        categoryDao.insertCategory(
            Category(
                id = "default_7",
                name = "Other",
                icon = "category",
                isCustom = 0L
            )
        )
        expenseDao.moveExpensesToCategory(
            oldCategoryId = "default_8",
            newCategoryId = "default_7"
        )
        categoryDao.deleteCategory("default_8")
    }

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses().distinctUntilChanged()

    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        expenseDao.getExpensesBetween(startMillis, endMillis).distinctUntilChanged()

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseDao.getAllExpensesSnapshot()

    suspend fun getExpensesSnapshotBetween(startMillis: Long, endMillis: Long): List<Expense> =
        expenseDao.getExpensesBetween(startMillis, endMillis).first()

    fun getAllIncomes(): Flow<List<Income>> = incomeDao.getAllIncomes().distinctUntilChanged()

    fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>> =
        incomeDao.getIncomesBetween(startMillis, endMillis).distinctUntilChanged()

    suspend fun getAllIncomesSnapshot(): List<Income> = incomeDao.getAllIncomesSnapshot()

    suspend fun getIncomesSnapshotBetween(startMillis: Long, endMillis: Long): List<Income> =
        incomeDao.getIncomesBetween(startMillis, endMillis).first()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSnapshot()

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
            val incomeAmount = incomeAmountGroup.toSummedAmount()
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

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getIncomeById(id: String): Income? = incomeDao.getIncomeById(id)

    suspend fun deleteExpense(id: String) {
        expenseDao.deleteExpense(id)
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun deleteCategory(id: String) {
        categoryDao.deleteCategory(id)
    }

    suspend fun isCategoryInUse(id: String): Boolean {
        return expenseDao.countExpensesForCategory(id) > 0L
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        expenseDao.deleteRecurringExpenseSeries(seriesId)
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun deleteIncome(id: String) {
        incomeDao.deleteIncome(id)
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        incomeDao.deleteRecurringIncomeSeries(seriesId)
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun insertIncome(
        id: String,
        amount: BigInteger,
        date: Long,
        description: String?,
        recurringSeriesId: String? = null
    ) {
        insertIncomes(
            listOf(
                PendingIncome(
                    id = id,
                    amount = amount,
                    date = date,
                    description = description,
                    recurringSeriesId = recurringSeriesId
                )
            )
        )
    }

    suspend fun insertIncomes(incomes: List<PendingIncome>) {
        if (incomes.isEmpty()) return

        writeTransaction {
            incomeDao.insertIncomes(
                incomes.map { income ->
                    Income(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId
                    )
                }
            )
        }
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: BigInteger,
        date: Long,
        description: String?
    ) {
        val seriesItems = incomeDao.getRecurringIncomesBySeries(seriesId)
            .map { income ->
                ExistingRecurringIncomeItem(
                    id = income.id,
                    date = income.date
                )
            }

        insertIncomes(
            buildUpdatedRecurringIncomeSeries(
                existingItems = seriesItems,
                anchorItemId = anchorIncomeId,
                anchorDate = date,
                amount = amount,
                description = description,
                recurringSeriesId = seriesId
            )
        )
    }

    suspend fun insertExpenses(expenses: List<PendingExpense>) {
        if (expenses.isEmpty()) return

        writeTransaction {
            expenseDao.insertExpenses(
                expenses.map { expense ->
                    Expense(
                        id = expense.id,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description,
                        isShared = if (expense.isShared) 1L else 0L,
                        recurringSeriesId = expense.recurringSeriesId
                    )
                }
            )
        }
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: BigInteger,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        val seriesItems = expenseDao.getRecurringExpensesBySeries(seriesId)
            .map { expense ->
                ExistingRecurringExpenseItem(
                    id = expense.id,
                    date = expense.date
                )
            }

        insertExpenses(
            buildUpdatedRecurringExpenseSeries(
                existingItems = seriesItems,
                anchorItemId = anchorExpenseId,
                anchorDate = date,
                amount = amount,
                categoryId = categoryId,
                description = description,
                isShared = isShared,
                recurringSeriesId = seriesId
            )
        )
    }

    suspend fun replaceAllData(
        categories: List<RestoredCategory>,
        expenses: List<PendingExpense>,
        incomes: List<PendingIncome>
    ) {
        writeTransaction {
            expenseDao.deleteAllExpenses()
            incomeDao.deleteAllIncomes()
            categoryDao.deleteAllCategories()

            categoryDao.insertCategories(
                categories.map { category ->
                    Category(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        isCustom = if (category.isCustom) 1L else 0L
                    )
                }
            )

            expenseDao.insertExpenses(
                expenses.map { expense ->
                    Expense(
                        id = expense.id,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description,
                        isShared = if (expense.isShared) 1L else 0L,
                        recurringSeriesId = expense.recurringSeriesId
                    )
                }
            )

            incomeDao.insertIncomes(
                incomes.map { income ->
                    Income(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId
                    )
                }
            )
        }
        HomeBudgetWidgetRefresh.requestRefresh()
    }

    private suspend fun <T> writeTransaction(block: suspend () -> T): T {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block.invoke()
            }
        }
    }
}

private fun buildDashboardMonthSummary(
    expenseSummary: ExpenseMonthSummaryRow,
    incomeAmount: BigInteger,
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
        highestDayAmount = highestDay?.amount ?: ZERO,
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
