package it.homebudget.app.data

import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ExpenseEntryRepository(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses().distinctUntilChanged()

    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        expenseDao.getExpensesBetween(startMillis, endMillis).distinctUntilChanged()

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseDao.getAllExpensesSnapshot()

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getRecurringExpensesBySeries(seriesId: String): List<Expense> {
        return expenseDao.getRecurringExpensesBySeries(seriesId)
    }

    suspend fun insertExpenses(expenses: List<PendingExpense>) {
        if (expenses.isEmpty()) return

        transactionRunner.runInTransaction {
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
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteExpense(id: String) {
        expenseDao.deleteExpense(id)
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        expenseDao.deleteRecurringExpenseSeries(seriesId)
        widgetRefreshCoordinator.requestRefresh()
    }
}
