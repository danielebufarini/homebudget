package it.homebudget.app.data

import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.Category
import it.homebudget.app.database.DEFAULT_CATEGORY_COLOR
import it.homebudget.app.database.HomeBudgetDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

private data class DefaultCategorySeed(
    val name: String,
    val icon: String,
    val color: String
)

class CategoryRepository(
    database: HomeBudgetDatabase,
    private val transactionRunner: DatabaseTransactionRunner
) {
    private val categoryDao = database.categoryDao()
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories().distinctUntilChanged()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSnapshot()

    suspend fun getCategoryById(id: String): Category? = categoryDao.getCategoryById(id)

    suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        isCustom: Boolean,
        color: String = DEFAULT_CATEGORY_COLOR,
        categoryType: String = CATEGORY_TYPE_EXPENSE,
        isArchived: Boolean = false
    ) {
        categoryDao.insertCategory(
            Category(
                id = id,
                name = name,
                icon = icon,
                color = color,
                categoryType = categoryType,
                isCustom = if (isCustom) 1L else 0L,
                isArchived = if (isArchived) 1L else 0L
            )
        )
    }

    suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String
    ) {
        categoryDao.updateCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType
        )
    }

    suspend fun deleteCategory(id: String) {
        val category = categoryDao.getCategoryById(id) ?: return
        if (category.isCustom == 1L) {
            categoryDao.setCategoryArchived(id = id, isArchived = 1L)
        } else {
            categoryDao.deleteCategory(id)
        }
    }

    suspend fun isCategoryInUse(id: String): Boolean {
        return expenseDao.countExpensesForCategory(id) > 0L || incomeDao.countIncomesForCategory(id) > 0L
    }

    suspend fun insertDefaultCategoriesIfEmpty() {
        transactionRunner.runInTransaction {
            if (categoryDao.countCategories() == 0L) {
                val defaults = listOf(
                    DefaultCategorySeed("Household", "home", "#2FA66A"),
                    DefaultCategorySeed("Food", "shopping_cart", "#E46C42"),
                    DefaultCategorySeed("Restaurant", "restaurant", "#D63871"),
                    DefaultCategorySeed("Car", "directions_car", "#2388D9"),
                    DefaultCategorySeed("Travel", "flight", "#5B6EE1"),
                    DefaultCategorySeed("Healthcare", "local_hospital", "#009688"),
                    DefaultCategorySeed("Personal", "person", "#6F45E9"),
                    DefaultCategorySeed("Other", "category", "#8D6E63")
                )
                categoryDao.insertCategories(
                    defaults.mapIndexed { index, category ->
                        Category(
                            id = "default_$index",
                            name = category.name,
                            icon = category.icon,
                            color = category.color,
                            categoryType = CATEGORY_TYPE_EXPENSE,
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
                color = "#8D6E63",
                categoryType = CATEGORY_TYPE_EXPENSE,
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
