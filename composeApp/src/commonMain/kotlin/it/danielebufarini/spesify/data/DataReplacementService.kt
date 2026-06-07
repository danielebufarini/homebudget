package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.rebuildAllSearchIndexes

class DataReplacementService(
    database: SpesifyDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val categoryDao = database.categoryDao()
    private val searchIndexDao = database.searchIndexDao()

    suspend fun replaceAllData(
        categories: List<RestoredCategory>,
        expenses: List<PendingExpense>,
        incomes: List<PendingIncome>
    ) {
        transactionRunner.runInTransaction {
            expenseDao.deleteAllExpenses()
            incomeDao.deleteAllIncomes()
            categoryDao.deleteAllCategories()

            categoryDao.insertCategories(
                categories.map { category ->
                    Category(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        color = category.color,
                        categoryType = category.categoryType,
                        isArchived = if (category.isArchived) 1L else 0L,
                        sortOrder = category.sortOrder
                    )
                }
            )

            expenseDao.insertExpenses(
                expenses.map(PendingExpense::toEntity)
            )

            incomeDao.insertIncomes(
                incomes.map(PendingIncome::toEntity)
            )

            searchIndexDao.rebuildAllSearchIndexes()
        }
        widgetRefreshCoordinator.refreshNow()
    }
}
