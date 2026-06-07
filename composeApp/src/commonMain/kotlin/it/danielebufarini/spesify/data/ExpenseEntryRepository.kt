package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CategoryUsageCountRow
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.refreshExpenseSearchRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ExpenseEntryRepository(
    database: SpesifyDatabase,
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

    fun searchExpenseCandidatePage(
        query: String,
        limit: Int,
        cursor: TransactionPageCursor?
    ): Flow<TransactionSearchPage<Expense>> {
        val ftsQuery = ftsSearchQuery(query) ?: return flowOf(
            TransactionSearchPage(
                items = emptyList(),
                nextCursor = null,
                canLoadMore = false
            )
        )
        val safeLimit = limit.coerceAtLeast(1)
        val pageFlow = if (cursor == null) {
            expenseDao.searchExpenses(
                ftsQuery = ftsQuery,
                limit = safeLimit + 1,
                offset = 0
            )
        } else {
            expenseDao.searchExpensesAfter(
                ftsQuery = ftsQuery,
                limit = safeLimit + 1,
                cursorDate = cursor.date,
                cursorId = cursor.id
            )
        }
        return pageFlow.map { expenses ->
            expenses.toTransactionSearchPage(
                limit = safeLimit,
                itemDate = Expense::date,
                itemId = Expense::id
            )
        }
    }

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseDao.getAllExpensesSnapshot()

    suspend fun getRecentExpensesSnapshot(limit: Int): List<Expense> =
        expenseDao.getRecentExpensesSnapshot(limit.coerceAtLeast(1))

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
