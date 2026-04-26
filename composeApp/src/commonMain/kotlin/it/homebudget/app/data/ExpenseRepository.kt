package it.homebudget.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.HomeBudgetDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExpenseRepository(private val database: HomeBudgetDatabase) {

    private val expenseQueries = database.expenseQueries
    private val categoryQueries = database.categoryQueries

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

    suspend fun getExpenseById(id: String): Expense? {
        return withContext(Dispatchers.IO) {
            expenseQueries.getExpenseById(id).executeAsOneOrNull()
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
        withContext(Dispatchers.IO) {
            expenseQueries.insertExpense(
                id = id,
                amount = amount,
                date = date,
                categoryId = categoryId,
                description = description,
                isShared = if (isShared) 1L else 0L
            )
        }
    }
}
