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

    fun getAllIncomes(): Flow<List<Income>> {
        return incomeQueries.getAllIncomes().asFlow().mapToList(Dispatchers.IO)
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

    suspend fun deleteIncome(id: String) {
        withContext(Dispatchers.IO) {
            incomeQueries.deleteIncome(id)
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
        description: String?
    ) {
        withContext(Dispatchers.IO) {
            incomeQueries.insertIncome(
                id = id,
                amount = amount,
                date = date,
                description = description
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

    suspend fun updateRecurringExpenseShared(seriesId: String, isShared: Boolean) {
        withContext(Dispatchers.IO) {
            expenseQueries.updateRecurringExpenseShared(
                isShared = if (isShared) 1L else 0L,
                recurringSeriesId = seriesId
            )
        }
    }
}
