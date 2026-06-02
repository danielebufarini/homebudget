package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.HomeBudgetDatabase
import it.danielebufarini.homebudget.database.refreshExpenseSearchRows
import it.danielebufarini.homebudget.database.refreshIncomeSearchRows

class RecurringTransactionService(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val searchIndexDao = database.searchIndexDao()

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    ) {
        transactionRunner.runInTransaction {
            val seriesItems = incomeDao.getRecurringIncomesBySeries(seriesId)
                .map { income ->
                    ExistingRecurringIncomeItem(
                        id = income.id,
                        date = income.date
                    )
                }
            val updatedIncomes = buildUpdatedRecurringIncomeSeries(
                existingItems = seriesItems,
                anchorItemId = anchorIncomeId,
                anchorDate = date,
                amount = amount,
                description = description,
                categoryId = categoryId,
                recurringSeriesId = seriesId
            )

            incomeDao.insertIncomes(
                updatedIncomes.map(PendingIncome::toEntity)
            )
            searchIndexDao.refreshIncomeSearchRows(updatedIncomes.map(PendingIncome::id))
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        transactionRunner.runInTransaction {
            val seriesItems = expenseDao.getRecurringExpensesBySeries(seriesId)
                .map { expense ->
                    ExistingRecurringExpenseItem(
                        id = expense.id,
                        date = expense.date
                    )
                }
            val updatedExpenses = buildUpdatedRecurringExpenseSeries(
                existingItems = seriesItems,
                anchorItemId = anchorExpenseId,
                anchorDate = date,
                amount = amount,
                categoryId = categoryId,
                description = description,
                isShared = isShared,
                recurringSeriesId = seriesId
            )

            expenseDao.insertExpenses(
                updatedExpenses.map(PendingExpense::toEntity)
            )
            searchIndexDao.refreshExpenseSearchRows(updatedExpenses.map(PendingExpense::id))
        }
        widgetRefreshCoordinator.requestRefresh()
    }
}
