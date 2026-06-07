package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CategoryUsageCountRow
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.refreshIncomeSearchRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class IncomeRepository(
    database: SpesifyDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val incomeDao = database.incomeDao()
    private val searchIndexDao = database.searchIndexDao()

    fun getAllIncomes(): Flow<List<Income>> = incomeDao.getAllIncomes().distinctUntilChanged()

    fun getIncomeCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>> =
        incomeDao.getIncomeCategoryUsageCounts().distinctUntilChanged()

    fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>> =
        incomeDao.getIncomesBetween(startMillis, endMillis).distinctUntilChanged()

    fun getIncomesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Income>> {
        return incomeDao.getIncomesPageBetween(
            startMillis = startMillis,
            endMillis = endMillis,
            limit = limit,
            offset = offset
        ).distinctUntilChanged()
    }

    fun searchIncomeCandidates(query: String, limit: Int, offset: Int = 0): Flow<List<Income>> {
        val ftsQuery = ftsSearchQuery(query) ?: return flowOf(emptyList())
        return incomeDao.searchIncomes(
            ftsQuery = ftsQuery,
            limit = limit.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0)
        ).distinctUntilChanged()
    }

    fun searchIncomeCandidatePage(
        query: String,
        limit: Int,
        cursor: TransactionPageCursor?
    ): Flow<TransactionSearchPage<Income>> {
        val ftsQuery = ftsSearchQuery(query) ?: return flowOf(
            TransactionSearchPage(
                items = emptyList(),
                nextCursor = null,
                canLoadMore = false
            )
        )
        val safeLimit = limit.coerceAtLeast(1)
        val pageFlow = if (cursor == null) {
            incomeDao.searchIncomes(
                ftsQuery = ftsQuery,
                limit = safeLimit + 1,
                offset = 0
            )
        } else {
            incomeDao.searchIncomesAfter(
                ftsQuery = ftsQuery,
                limit = safeLimit + 1,
                cursorDate = cursor.date,
                cursorId = cursor.id
            )
        }
        return pageFlow.map { incomes ->
            incomes.toTransactionSearchPage(
                limit = safeLimit,
                itemDate = Income::date,
                itemId = Income::id
            )
        }
    }

    suspend fun getAllIncomesSnapshot(): List<Income> = incomeDao.getAllIncomesSnapshot()

    suspend fun getIncomesSnapshotBetween(startMillis: Long, endMillis: Long): List<Income> =
        incomeDao.getIncomesSnapshotBetween(startMillis, endMillis)

    suspend fun getIncomeById(id: String): Income? = incomeDao.getIncomeById(id)

    suspend fun getRecurringIncomesBySeries(seriesId: String): List<Income> {
        return incomeDao.getRecurringIncomesBySeries(seriesId)
    }

    suspend fun insertIncome(
        id: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String? = null,
        recurringSeriesId: String? = null
    ) {
        insertIncomes(
            listOf(
                PendingIncome(
                    id = id,
                    amount = amount,
                    date = date,
                    description = description,
                    recurringSeriesId = recurringSeriesId,
                    categoryId = categoryId
                )
            )
        )
    }

    suspend fun insertIncomes(incomes: List<PendingIncome>) {
        if (incomes.isEmpty()) return

        transactionRunner.runInTransaction {
            val ids = incomes.map(PendingIncome::id)
            incomeDao.insertIncomes(
                incomes.map(PendingIncome::toEntity)
            )
            searchIndexDao.refreshIncomeSearchRows(ids)
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteIncome(id: String) {
        transactionRunner.runInTransaction {
            searchIndexDao.deleteIncomeSearchRows(listOf(id))
            incomeDao.deleteIncome(id)
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        transactionRunner.runInTransaction {
            val ids = incomeDao.getRecurringIncomesBySeries(seriesId).map(Income::id)
            if (ids.isNotEmpty()) {
                searchIndexDao.deleteIncomeSearchRows(ids)
            }
            incomeDao.deleteRecurringIncomeSeries(seriesId)
        }
        widgetRefreshCoordinator.requestRefresh()
    }
}
