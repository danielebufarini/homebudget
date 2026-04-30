package it.homebudget.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExpenseRepository(private val database: HomeBudgetDatabase) {

    private val expenseQueries = database.expenseQueries
    private val categoryQueries = database.categoryQueries
    private val incomeQueries = database.incomeQueries

    fun getAllCategories(): Flow<List<Category>> {
        return categoryQueries.getAllCategories().asFlow().mapToList(Dispatchers.IO)
    }

    suspend fun insertCategory(id: String, name: String, icon: String, isCustom: Boolean) {
        withContext(Dispatchers.IO) {
            categoryQueries.insertCategory(
                id = id,
                name = name,
                icon = icon,
                isCustom = if (isCustom) 1L else 0L
            )
        }
    }

    suspend fun insertDefaultCategoriesIfEmpty() {
        withContext(Dispatchers.IO) {
            database.transaction {
                val count = categoryQueries.countCategories().executeAsOne()
                if (count == 0L) {
                    val defaults = listOf(
                        "Spese casa" to "home",
                        "Cibo" to "restaurant",
                        "Bollette" to "receipt",
                        "Spese auto" to "directions_car",
                        "Varie" to "category"
                    )
                    defaults.forEachIndexed { index, pair ->
                        categoryQueries.insertCategory(
                            id = "default_$index",
                            name = pair.first,
                            icon = pair.second,
                            isCustom = 0L
                        )
                    }
                }
            }
        }
    }

    fun getAllExpenses(): Flow<List<Expense>> {
        return expenseQueries.getAllExpenses().asFlow().mapToList(Dispatchers.IO)
    }

    suspend fun getAllExpensesSnapshot(): List<Expense> {
        return withContext(Dispatchers.IO) {
            expenseQueries.getAllExpenses().executeAsList()
        }
    }

    fun getAllIncomes(): Flow<List<Income>> {
        return incomeQueries.getAllIncomes().asFlow().mapToList(Dispatchers.IO)
    }

    suspend fun getAllCategoriesSnapshot(): List<Category> {
        return withContext(Dispatchers.IO) {
            categoryQueries.getAllCategories().executeAsList()
        }
    }

    suspend fun getExpenseById(id: String): Expense? {
        return withContext(Dispatchers.IO) {
            expenseQueries.getExpenseById(id).executeAsOneOrNull()
        }
    }

    suspend fun getIncomeById(id: String): Income? {
        return withContext(Dispatchers.IO) {
            incomeQueries.getIncomeById(id).executeAsOneOrNull()
        }
    }

    suspend fun deleteExpense(id: String) {
        withContext(Dispatchers.IO) {
            expenseQueries.deleteExpense(id)
        }
    }

    suspend fun deleteCategory(id: String) {
        withContext(Dispatchers.IO) {
            categoryQueries.deleteCategory(id)
        }
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        withContext(Dispatchers.IO) {
            expenseQueries.deleteRecurringExpenseSeries(seriesId)
        }
    }

    suspend fun deleteIncome(id: String) {
        withContext(Dispatchers.IO) {
            incomeQueries.deleteIncome(id)
        }
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        withContext(Dispatchers.IO) {
            incomeQueries.deleteRecurringIncomeSeries(seriesId)
        }
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
        withContext(Dispatchers.IO) {
            database.transaction {
                incomes.forEach { income ->
                    incomeQueries.insertIncome(
                        id = income.id,
                        amount = income.amount,
                        date = income.date,
                        description = income.description,
                        recurringSeriesId = income.recurringSeriesId
                    )
                }
            }
        }
    }

    suspend fun cancelRecurringIncomes(seriesId: String, fromDate: Long) {
        withContext(Dispatchers.IO) {
            incomeQueries.deleteRecurringIncomesFrom(
                recurringSeriesId = seriesId,
                date = fromDate
            )
        }
    }

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: BigInteger,
        date: Long,
        description: String?
    ) {
        withContext(Dispatchers.IO) {
            val seriesItems = incomeQueries.getRecurringIncomesBySeries(seriesId)
                .executeAsList()
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
    }

    suspend fun insertExpenses(expenses: List<PendingExpense>) {
        withContext(Dispatchers.IO) {
            database.transaction {
                expenses.forEach { expense ->
                    expenseQueries.insertExpense(
                        id = expense.id,
                        amount = expense.amount,
                        date = expense.date,
                        categoryId = expense.categoryId,
                        description = expense.description,
                        isShared = if (expense.isShared) 1L else 0L,
                        recurringSeriesId = expense.recurringSeriesId
                    )
                }
            }
        }
    }

    suspend fun cancelRecurringExpenses(seriesId: String, fromDate: Long) {
        withContext(Dispatchers.IO) {
            expenseQueries.deleteRecurringExpensesFrom(
                recurringSeriesId = seriesId,
                date = fromDate
            )
        }
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
        withContext(Dispatchers.IO) {
            val seriesItems = expenseQueries.getRecurringExpensesBySeries(seriesId)
                .executeAsList()
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
    }

    suspend fun updateRecurringExpenseShared(seriesId: String, isShared: Boolean) {
        withContext(Dispatchers.IO) {
            expenseQueries.updateRecurringExpenseShared(
                isShared = if (isShared) 1L else 0L,
                recurringSeriesId = seriesId
            )
        }
    }
}
