package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.DEFAULT_CATEGORY_COLOR
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.refreshExpenseSearchRows
import it.danielebufarini.spesify.database.refreshIncomeSearchRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.getString
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.category_default_0
import spesify.composeapp.generated.resources.category_default_1
import spesify.composeapp.generated.resources.category_default_2
import spesify.composeapp.generated.resources.category_default_3
import spesify.composeapp.generated.resources.category_default_4
import spesify.composeapp.generated.resources.category_default_5
import spesify.composeapp.generated.resources.category_default_6
import spesify.composeapp.generated.resources.category_default_7

private data class StarterCategorySeed(
    val id: String,
    val name: String,
    val icon: String,
    val color: String
)

class CategoryRepository(
    database: SpesifyDatabase,
    private val transactionRunner: DatabaseTransactionRunner
) {
    private val categoryDao = database.categoryDao()
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val searchIndexDao = database.searchIndexDao()

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories().distinctUntilChanged()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSnapshot()

    suspend fun getCategoryById(id: String): Category? = categoryDao.getCategoryById(id)

    suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String = DEFAULT_CATEGORY_COLOR,
        categoryType: String = CATEGORY_TYPE_EXPENSE,
        isArchived: Boolean = false,
        sortOrder: Long? = null
    ) {
        val resolvedSortOrder = sortOrder ?: nextSortOrder()
        categoryDao.insertCategory(
            Category(
                id = id,
                name = name,
                icon = icon,
                color = color,
                categoryType = categoryType,
                isArchived = if (isArchived) 1L else 0L,
                sortOrder = resolvedSortOrder
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
        transactionRunner.runInTransaction {
            val affectedExpenseIds = expenseDao.getExpenseIdsForCategory(id)
            val affectedIncomeIds = incomeDao.getIncomeIdsForCategory(id)
            categoryDao.updateCategory(
                id = id,
                name = name,
                icon = icon,
                color = color,
                categoryType = categoryType
            )
            searchIndexDao.refreshExpenseSearchRows(affectedExpenseIds)
            searchIndexDao.refreshIncomeSearchRows(affectedIncomeIds)
        }
    }

    suspend fun setCategoryArchived(id: String, isArchived: Boolean) {
        categoryDao.setCategoryArchived(
            id = id,
            isArchived = if (isArchived) 1L else 0L
        )
    }

    suspend fun updateCategorySortOrder(id: String, sortOrder: Long) {
        categoryDao.updateCategorySortOrder(id = id, sortOrder = sortOrder)
    }

    suspend fun deleteCategory(id: String) {
        transactionRunner.runInTransaction {
            val category = categoryDao.getCategoryById(id) ?: return@runInTransaction
            if (isCategoryInUseInternal(category.id)) {
                categoryDao.setCategoryArchived(id = id, isArchived = 1L)
            } else {
                categoryDao.deleteCategory(id)
            }
        }
    }

    suspend fun isCategoryInUse(id: String): Boolean {
        return isCategoryInUseInternal(id)
    }

    suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) {
        require(sourceCategoryId != targetCategoryId) { "Source and target categories must differ." }

        transactionRunner.runInTransaction {
            val sourceCategory = categoryDao.getCategoryById(sourceCategoryId)
                ?: error("Category $sourceCategoryId not found.")
            val targetCategory = categoryDao.getCategoryById(targetCategoryId)
                ?: error("Category $targetCategoryId not found.")

            val expenseUsageCount = expenseDao.countExpensesForCategory(sourceCategory.id)
            val incomeUsageCount = incomeDao.countIncomesForCategory(sourceCategory.id)
            val affectedExpenseIds = if (expenseUsageCount > 0L) {
                expenseDao.getExpenseIdsForCategory(sourceCategory.id)
            } else {
                emptyList()
            }
            val affectedIncomeIds = if (incomeUsageCount > 0L) {
                incomeDao.getIncomeIdsForCategory(sourceCategory.id)
            } else {
                emptyList()
            }

            if (expenseUsageCount > 0L) {
                require(targetCategory.categoryType == CATEGORY_TYPE_EXPENSE) {
                    "Expense transactions can only be reassigned to an expense category."
                }
                expenseDao.moveExpensesToCategory(
                    oldCategoryId = sourceCategory.id,
                    newCategoryId = targetCategory.id
                )
            }

            if (incomeUsageCount > 0L) {
                require(targetCategory.categoryType == CATEGORY_TYPE_INCOME) {
                    "Income transactions can only be reassigned to a compatible category."
                }
                incomeDao.moveIncomesToCategory(
                    oldCategoryId = sourceCategory.id,
                    newCategoryId = targetCategory.id
                )
            }

            if (!isCategoryInUseInternal(sourceCategory.id)) {
                categoryDao.deleteCategory(sourceCategory.id)
            }
            searchIndexDao.refreshExpenseSearchRows(affectedExpenseIds)
            searchIndexDao.refreshIncomeSearchRows(affectedIncomeIds)
        }
    }

    suspend fun seedStarterCategoriesIfEmpty() {
        val starterCategories = loadStarterCategories()
        transactionRunner.runInTransaction {
            if (categoryDao.countCategories() == 0L) {
                categoryDao.insertCategories(
                    starterCategories.mapIndexed { index, category ->
                        Category(
                            id = category.id,
                            name = category.name,
                            icon = category.icon,
                            color = category.color,
                            categoryType = CATEGORY_TYPE_EXPENSE,
                            sortOrder = index.toLong()
                        )
                    }
                )
            }
        }
    }

    private suspend fun nextSortOrder(): Long = (categoryDao.getMaxSortOrder() ?: -1L) + 1L

    private suspend fun isCategoryInUseInternal(id: String): Boolean {
        return expenseDao.countExpensesForCategory(id) > 0L || incomeDao.countIncomesForCategory(id) > 0L
    }

    private suspend fun loadStarterCategories(): List<StarterCategorySeed> {
        return listOf(
            StarterCategorySeed(
                id = "starter_expense_household",
                name = getString(Res.string.category_default_0),
                icon = "home",
                color = "#2FA66A"
            ),
            StarterCategorySeed(
                id = "starter_expense_food",
                name = getString(Res.string.category_default_1),
                icon = "shopping_cart",
                color = "#E46C42"
            ),
            StarterCategorySeed(
                id = "starter_expense_restaurant",
                name = getString(Res.string.category_default_2),
                icon = "restaurant",
                color = "#D63871"
            ),
            StarterCategorySeed(
                id = "starter_expense_car",
                name = getString(Res.string.category_default_3),
                icon = "directions_car",
                color = "#2388D9"
            ),
            StarterCategorySeed(
                id = "starter_expense_travel",
                name = getString(Res.string.category_default_4),
                icon = "flight",
                color = "#5B6EE1"
            ),
            StarterCategorySeed(
                id = "starter_expense_healthcare",
                name = getString(Res.string.category_default_5),
                icon = "local_hospital",
                color = "#009688"
            ),
            StarterCategorySeed(
                id = "starter_expense_personal",
                name = getString(Res.string.category_default_6),
                icon = "person",
                color = "#6F45E9"
            ),
            StarterCategorySeed(
                id = "starter_expense_other",
                name = getString(Res.string.category_default_7),
                icon = "category",
                color = "#8D6E63"
            )
        )
    }
}
