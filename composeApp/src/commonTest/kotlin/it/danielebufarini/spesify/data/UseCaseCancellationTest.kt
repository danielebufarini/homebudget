package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UseCaseCancellationTest {
    @Test
    fun assistantTransaction_cancellationDuringCategoryLoadDoesNotBecomeFailedResult() = runTest {
        val cancellation = CancellationException("category loading cancelled")
        val writes = EditorRecordingRepository()
        assertFailsWith<CancellationException> {
            AddTransactionUseCase(CancellationCategories(cancellation), writes).execute(
                AddTransactionCommand(TransactionKind.Expense, 1234L, categoryName = "food")
            )
        }
        assertTrue(writes.expenses.isEmpty())
    }

    @Test
    fun assistantTransaction_cancellationDuringWriteDoesNotBecomeFailedResult() = runTest {
        val writes = EditorRecordingRepository().apply { failure = CancellationException("write cancelled") }
        assertFailsWith<CancellationException> {
            AddTransactionUseCase(CancellationCategories(), writes).execute(
                AddTransactionCommand(TransactionKind.Expense, 1234L, categoryName = "food")
            )
        }
        assertTrue(writes.expenses.isEmpty())
    }

    @Test
    fun categoryAgent_cancellationDoesNotBecomeAnOrdinaryFailure() = runTest {
        assertFailsWith<CancellationException> {
            CategoryAgentUseCase(CancellationCategories(CancellationException("cancelled"))).listCategories(null)
        }
    }
}

private class CancellationCategories(private val failure: Throwable? = null) : CategoryManagementRepository {
    private val rows = listOf(Category(id = "food", name = "Food", icon = "category"))
    override fun getAllCategories() = flowOf(rows)
    override suspend fun getAllCategoriesSnapshot() = rows
    override suspend fun seedStarterCategoriesIfEmpty() { failure?.let { throw it } }
    override suspend fun insertCategory(id: String, name: String, icon: String, color: String, categoryType: String, isArchived: Boolean, sortOrder: Long?) = Unit
    override suspend fun updateCategory(id: String, name: String, icon: String, color: String?, categoryType: String?) = Unit
    override suspend fun deleteCategory(id: String) = Unit
    override suspend fun isCategoryInUse(id: String) = false
    override suspend fun setCategoryArchived(id: String, isArchived: Boolean) = Unit
    override suspend fun updateCategorySortOrder(id: String, sortOrder: Long) = Unit
    override suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) = Unit
}
