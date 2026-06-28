package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.DEFAULT_CATEGORY_COLOR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AddTransactionUseCaseTest {
    @Test
    fun `expense creation requires a category`() = runTest {
        val writes = RecordingTransactionWriteRepository()
        val useCase = AddTransactionUseCase(
            categoryRepository = FakeCategoryManagementRepository(categories = emptyList()),
            transactionWriteRepository = writes
        )

        val result = useCase.execute(
            AddTransactionCommand(
                kind = TransactionKind.Expense,
                amount = 1_250L,
                source = TransactionCreationSource.AndroidAppFunction
            )
        )

        assertIs<AddTransactionResult.NeedsConfirmation>(result)
        assertTrue(result.message.contains("category", ignoreCase = true))
        assertTrue(writes.expenses.isEmpty())
        assertTrue(writes.incomes.isEmpty())
    }

    @Test
    fun `income creation keeps amount as minor-unit long`() = runTest {
        val writes = RecordingTransactionWriteRepository()
        val useCase = AddTransactionUseCase(
            categoryRepository = FakeCategoryManagementRepository(categories = emptyList()),
            transactionWriteRepository = writes
        )

        val result = useCase.execute(
            AddTransactionCommand(
                kind = TransactionKind.Income,
                amount = 120_000L,
                note = "Salary",
                source = TransactionCreationSource.IosAppIntent
            )
        )

        assertIs<AddTransactionResult.Created>(result)
        assertEquals(120_000L, writes.incomes.single().amount)
        assertEquals("Salary", writes.incomes.single().description)
        assertTrue(writes.expenses.isEmpty())
    }

    @Test
    fun `expense creation resolves category names case insensitively`() = runTest {
        val writes = RecordingTransactionWriteRepository()
        val useCase = AddTransactionUseCase(
            categoryRepository = FakeCategoryManagementRepository(
                categories = listOf(
                    Category(
                        id = "food",
                        name = "Food",
                        icon = "shopping_cart",
                        color = DEFAULT_CATEGORY_COLOR,
                        categoryType = CATEGORY_TYPE_EXPENSE
                    )
                )
            ),
            transactionWriteRepository = writes
        )

        val result = useCase.execute(
            AddTransactionCommand(
                kind = TransactionKind.Expense,
                amount = 990L,
                categoryName = " food ",
                source = TransactionCreationSource.AndroidAppFunction
            )
        )

        assertIs<AddTransactionResult.Created>(result)
        assertEquals(990L, writes.expenses.single().amount)
        assertEquals("food", writes.expenses.single().categoryId)
    }
}

private class FakeCategoryManagementRepository(
    private val categories: List<Category>
) : CategoryManagementRepository {
    override fun getAllCategories(): Flow<List<Category>> = flowOf(categories)

    override suspend fun getAllCategoriesSnapshot(): List<Category> = categories

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

    override suspend fun seedStarterCategoriesIfEmpty() = Unit

    override suspend fun deleteCategory(id: String) = Unit

    override suspend fun isCategoryInUse(id: String): Boolean = false

    override suspend fun setCategoryArchived(id: String, isArchived: Boolean) = Unit

    override suspend fun updateCategorySortOrder(id: String, sortOrder: Long) = Unit

    override suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) = Unit
}

private class RecordingTransactionWriteRepository : TransactionWriteRepository {
    val expenses = mutableListOf<PendingExpense>()
    val incomes = mutableListOf<PendingIncome>()

    override suspend fun deleteExpense(id: String) = Unit

    override suspend fun deleteRecurringExpenseSeries(seriesId: String) = Unit

    override suspend fun deleteIncome(id: String) = Unit

    override suspend fun deleteRecurringIncomeSeries(seriesId: String) = Unit

    override suspend fun insertIncome(
        id: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?,
        recurringSeriesId: String?
    ) {
        incomes += PendingIncome(
            id = id,
            amount = amount,
            date = date,
            description = description,
            recurringSeriesId = recurringSeriesId,
            categoryId = categoryId
        )
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        this.incomes += incomes
    }

    override suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    ) = Unit

    override suspend fun insertExpenses(expenses: List<PendingExpense>) {
        this.expenses += expenses
    }

    override suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) = Unit
}
