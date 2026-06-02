package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.CategoryUsageCountRow
import it.danielebufarini.homebudget.database.HomeBudgetDatabase
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.database.refreshIncomeSearchRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class IncomeRepository(
    database: HomeBudgetDatabase,
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
