package it.homebudget.app.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Income
import kotlinx.coroutines.flow.Flow

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

class ExpenseRepository(private val database: HomeBudgetDatabase) {

    private val expenseDao = database.expenseDao()
    private val categoryDao = database.categoryDao()
    private val incomeDao = database.incomeDao()

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

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
                    DefaultCategorySeed("Household expenses", "home"),
                    DefaultCategorySeed("Food", "shopping_cart"),
                    DefaultCategorySeed("Restaurant", "restaurant"),
                    DefaultCategorySeed("Car expenses", "directions_car"),
                    DefaultCategorySeed("Travel", "flight"),
                    DefaultCategorySeed("Healthcare expenses", "local_hospital"),
                    DefaultCategorySeed("Bills", "receipt"),
                    DefaultCategorySeed("Personal expenses", "person"),
                    DefaultCategorySeed("Miscellaneous", "category")
                )
                defaults.forEachIndexed { index, category ->
                    categoryDao.insertCategory(
                        Category(
                            id = "default_$index",
                            name = category.name,
                            icon = category.icon,
                            isCustom = 0L
                        )
                    )
                }
            }
        }
    }

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseDao.getAllExpensesSnapshot()

    fun getAllIncomes(): Flow<List<Income>> = incomeDao.getAllIncomes()

    suspend fun getAllIncomesSnapshot(): List<Income> = incomeDao.getAllIncomesSnapshot()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSnapshot()

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getIncomeById(id: String): Income? = incomeDao.getIncomeById(id)

    suspend fun deleteExpense(id: String) {
        expenseDao.deleteExpense(id)
    }

    suspend fun deleteCategory(id: String) {
        categoryDao.deleteCategory(id)
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        expenseDao.deleteRecurringExpenseSeries(seriesId)
    }

    suspend fun deleteIncome(id: String) {
        incomeDao.deleteIncome(id)
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        incomeDao.deleteRecurringIncomeSeries(seriesId)
    }

    suspend fun insertExpense(
        id: String,
        amount: BigInteger,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        insertExpenses(
            listOf(
                PendingExpense(
                    id = id,
                    amount = amount,
                    date = date,
                    categoryId = categoryId,
                    description = description,
                    isShared = isShared,
                    recurringSeriesId = null
                )
            )
        )
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
        writeTransaction {
            incomes.forEach { income ->
                incomeDao.insertIncome(
                    Income(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId
                    )
                )
            }
        }
    }

    suspend fun cancelRecurringIncomes(seriesId: String, fromDate: Long) {
        incomeDao.deleteRecurringIncomesFrom(seriesId = seriesId, fromDate = fromDate)
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
        writeTransaction {
            expenses.forEach { expense ->
                expenseDao.insertExpense(
                    Expense(
                        id = expense.id,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description,
                        isShared = if (expense.isShared) 1L else 0L,
                        recurringSeriesId = expense.recurringSeriesId
                    )
                )
            }
        }
    }

    suspend fun cancelRecurringExpenses(seriesId: String, fromDate: Long) {
        expenseDao.deleteRecurringExpensesFrom(seriesId = seriesId, fromDate = fromDate)
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

    suspend fun updateRecurringExpenseShared(seriesId: String, isShared: Boolean) {
        expenseDao.updateRecurringExpenseShared(
            seriesId = seriesId,
            isShared = if (isShared) 1L else 0L
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

            categories.forEach { category ->
                categoryDao.insertCategory(
                    Category(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        isCustom = if (category.isCustom) 1L else 0L
                    )
                )
            }

            expenses.forEach { expense ->
                expenseDao.insertExpense(
                    Expense(
                        id = expense.id,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description,
                        isShared = if (expense.isShared) 1L else 0L,
                        recurringSeriesId = expense.recurringSeriesId
                    )
                )
            }

            incomes.forEach { income ->
                incomeDao.insertIncome(
                    Income(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId
                    )
                )
            }
        }
    }

    private suspend fun <T> writeTransaction(block: suspend () -> T): T {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block.invoke()
            }
        }
    }
}
