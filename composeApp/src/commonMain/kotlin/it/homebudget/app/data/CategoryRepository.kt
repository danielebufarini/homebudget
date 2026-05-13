package it.homebudget.app.data

import it.homebudget.app.database.Category
import it.homebudget.app.database.HomeBudgetDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

private data class DefaultCategorySeed(
    val name: String,
    val icon: String
)

class CategoryRepository(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner
) {
    private val categoryDao = database.categoryDao()
    private val expenseDao = database.expenseDao()

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories().distinctUntilChanged()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSnapshot()

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

    suspend fun deleteCategory(id: String) {
        categoryDao.deleteCategory(id)
    }

    suspend fun isCategoryInUse(id: String): Boolean {
        return expenseDao.countExpensesForCategory(id) > 0L
    }

    suspend fun insertDefaultCategoriesIfEmpty() {
        transactionRunner.runInTransaction {
            if (categoryDao.countCategories() == 0L) {
                val defaults = listOf(
                    DefaultCategorySeed("Household", "home"),
                    DefaultCategorySeed("Food", "shopping_cart"),
                    DefaultCategorySeed("Restaurant", "restaurant"),
                    DefaultCategorySeed("Car", "directions_car"),
                    DefaultCategorySeed("Travel", "flight"),
                    DefaultCategorySeed("Healthcare", "local_hospital"),
                    DefaultCategorySeed("Personal", "person"),
                    DefaultCategorySeed("Other", "category")
                )
                categoryDao.insertCategories(
                    defaults.mapIndexed { index, category ->
                        Category(
                            id = "default_$index",
                            name = category.name,
                            icon = category.icon,
                            isCustom = 0L
                        )
                    }
                )
            } else {
                normalizeDefaultCategories()
            }
        }
    }

    private suspend fun normalizeDefaultCategories() {
        categoryDao.insertCategory(
            Category(
                id = "default_7",
                name = "Other",
                icon = "category",
                isCustom = 0L
            )
        )
        expenseDao.moveExpensesToCategory(
            oldCategoryId = "default_8",
            newCategoryId = "default_7"
        )
        categoryDao.deleteCategory("default_8")
    }
}
