package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultMerchantCategoryResolverTest {
    @Test
    fun resolvesKnownFoodMerchantToFoodCategory() = runTest {
        val resolver = DefaultMerchantCategoryResolver(
            FakeCategoryRepository(
                listOf(
                    category("starter_expense_food", "Food"),
                    category("starter_expense_other", "Other")
                )
            )
        )

        assertEquals("starter_expense_food", resolver.resolveCategoryId("Esselunga"))
    }

    @Test
    fun fallsBackToOtherCategoryWhenMerchantIsUnknown() = runTest {
        val resolver = DefaultMerchantCategoryResolver(
            FakeCategoryRepository(
                listOf(
                    category("starter_expense_food", "Food"),
                    category("starter_expense_other", "Other")
                )
            )
        )

        assertEquals("starter_expense_other", resolver.resolveCategoryId("Unknown Merchant"))
    }

    @Test
    fun returnsNullWhenNoSafeFallbackExists() = runTest {
        val resolver = DefaultMerchantCategoryResolver(
            FakeCategoryRepository(listOf(category("starter_expense_food", "Food")))
        )

        assertNull(resolver.resolveCategoryId(null))
    }

    private fun category(id: String, name: String) = Category(
        id = id,
        name = name,
        icon = "category",
        categoryType = CATEGORY_TYPE_EXPENSE
    )

    private class FakeCategoryRepository(
        private val categories: List<Category>
    ) : CategoryManagementRepository {
        override fun getAllCategories(): Flow<List<Category>> = flowOf(categories)
        override suspend fun getAllCategoriesSnapshot(): List<Category> = categories
        override suspend fun seedStarterCategoriesIfEmpty() = Unit
        override suspend fun insertCategory(
            id: String,
            name: String,
            icon: String,
            color: String,
            categoryType: String,
            isArchived: Boolean,
            sortOrder: Long?
        ) = Unit
        override suspend fun updateCategory(
            id: String,
            name: String,
            icon: String,
            color: String?,
            categoryType: String?
        ) = Unit
        override suspend fun deleteCategory(id: String) = Unit
        override suspend fun isCategoryInUse(id: String): Boolean = false
        override suspend fun setCategoryArchived(id: String, isArchived: Boolean) = Unit
        override suspend fun updateCategorySortOrder(id: String, sortOrder: Long) = Unit
        override suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) = Unit
    }
}
