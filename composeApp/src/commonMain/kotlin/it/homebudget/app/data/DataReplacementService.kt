package it.homebudget.app.data

import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Income

class DataReplacementService(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val categoryDao = database.categoryDao()

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
        widgetRefreshCoordinator.requestRefresh()
    }
}
