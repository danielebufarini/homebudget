package it.homebudget.app.data

import it.homebudget.app.database.CategoryUsageCountRow
import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.refreshExpenseSearchRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class ExpenseEntryRepository(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()
    private val searchIndexDao = database.searchIndexDao()

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses().distinctUntilChanged()

    fun getExpenseCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>> =
        expenseDao.getExpenseCategoryUsageCounts().distinctUntilChanged()

    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        expenseDao.getExpensesBetween(startMillis, endMillis).distinctUntilChanged()

    fun getExpensesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>> {
        return expenseDao.getExpensesPageBetween(
            startMillis = startMillis,
            endMillis = endMillis,
            limit = limit,
            offset = offset
        ).distinctUntilChanged()
    }

    fun searchExpenseCandidates(query: String, limit: Int, offset: Int = 0): Flow<List<Expense>> {
        val ftsQuery = ftsSearchQuery(query) ?: return flowOf(emptyList())
        return expenseDao.searchExpenses(
            ftsQuery = ftsQuery,
            limit = limit.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0)
        ).distinctUntilChanged()
    }

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseDao.getAllExpensesSnapshot()

    suspend fun getExpensesSnapshotBetween(startMillis: Long, endMillis: Long): List<Expense> =
        expenseDao.getExpensesSnapshotBetween(startMillis, endMillis)

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getRecurringExpensesBySeries(seriesId: String): List<Expense> {
        return expenseDao.getRecurringExpensesBySeries(seriesId)
    }

    suspend fun insertExpenses(expenses: List<PendingExpense>) {
        if (expenses.isEmpty()) return

        transactionRunner.runInTransaction {
            val ids = expenses.map(PendingExpense::id)
            expenseDao.insertExpenses(
                expenses.map(PendingExpense::toEntity)
            )
            searchIndexDao.refreshExpenseSearchRows(ids)
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteExpense(id: String) {
        transactionRunner.runInTransaction {
            searchIndexDao.deleteExpenseSearchRows(listOf(id))
            expenseDao.deleteExpense(id)
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        transactionRunner.runInTransaction {
            val ids = expenseDao.getRecurringExpensesBySeries(seriesId).map(Expense::id)
            if (ids.isNotEmpty()) {
                searchIndexDao.deleteExpenseSearchRows(ids)
            }
            expenseDao.deleteRecurringExpenseSeries(seriesId)
        }
        widgetRefreshCoordinator.requestRefresh()
    }
}
