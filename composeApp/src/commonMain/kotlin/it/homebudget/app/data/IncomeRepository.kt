package it.homebudget.app.data
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class IncomeRepository(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val incomeDao = database.incomeDao()

    fun getAllIncomes(): Flow<List<Income>> = incomeDao.getAllIncomes().distinctUntilChanged()

    fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>> =
        incomeDao.getIncomesBetween(startMillis, endMillis).distinctUntilChanged()

    suspend fun getAllIncomesSnapshot(): List<Income> = incomeDao.getAllIncomesSnapshot()

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
            incomeDao.insertIncomes(
                incomes.map { income ->
                    Income(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId,
                        categoryId = income.categoryId
                    )
                }
            )
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteIncome(id: String) {
        incomeDao.deleteIncome(id)
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        incomeDao.deleteRecurringIncomeSeries(seriesId)
        widgetRefreshCoordinator.requestRefresh()
    }
}
