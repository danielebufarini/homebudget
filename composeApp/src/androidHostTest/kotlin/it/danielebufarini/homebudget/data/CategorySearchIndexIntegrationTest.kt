package it.danielebufarini.homebudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.danielebufarini.homebudget.data.csv.CsvImportResult
import it.danielebufarini.homebudget.data.csv.CsvImportStore
import it.danielebufarini.homebudget.data.csv.importBudgetItemsFromCsv
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.HomeBudgetDatabase
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CategorySearchIndexIntegrationTest {

    @Test
    fun expenseCategoryRename_refreshesAffectedExpenseSearchRow() = runTest {
        withFixture {
            insertCategory("expense_alpha", "AlphaExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("expense_1", "expense_alpha", "neutral expense description")

            assertContainsExpense("AlphaExpenseCategory", "expense_1")

            categoryRepository.updateCategory(
                id = "expense_alpha",
                name = "BetaExpenseCategory",
                icon = "category",
                color = "#123456",
                categoryType = CATEGORY_TYPE_EXPENSE
            )

            assertContainsExpense("BetaExpenseCategory", "expense_1")
            assertDoesNotContainExpense("AlphaExpenseCategory", "expense_1")
        }
    }

    @Test
    fun incomeCategoryRename_refreshesAffectedIncomeSearchRow() = runTest {
        withFixture {
            insertCategory("income_alpha", "AlphaIncomeCategory", CATEGORY_TYPE_INCOME)
            insertIncome("income_1", "income_alpha", "neutral income description")

            assertContainsIncome("AlphaIncomeCategory", "income_1")

            categoryRepository.updateCategory(
                id = "income_alpha",
                name = "BetaIncomeCategory",
                icon = "category",
                color = "#123456",
                categoryType = CATEGORY_TYPE_INCOME
            )

            assertContainsIncome("BetaIncomeCategory", "income_1")
            assertDoesNotContainIncome("AlphaIncomeCategory", "income_1")
        }
    }

    @Test
    fun categoryUsedByExpensesAndIncomes_refreshesBothSearchTablesOnRename() = runTest {
        withFixture {
            insertCategory("shared_category", "SharedAlphaCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("expense_shared", "shared_category", "expense neutral text")
            insertIncome("income_shared", "shared_category", "income neutral text")

            assertContainsExpense("SharedAlphaCategory", "expense_shared")
            assertContainsIncome("SharedAlphaCategory", "income_shared")

            categoryRepository.updateCategory(
                id = "shared_category",
                name = "SharedBetaCategory",
                icon = "category",
                color = "#123456",
                categoryType = CATEGORY_TYPE_EXPENSE
            )

            assertContainsExpense("SharedBetaCategory", "expense_shared")
            assertContainsIncome("SharedBetaCategory", "income_shared")
            assertDoesNotContainExpense("SharedAlphaCategory", "expense_shared")
            assertDoesNotContainIncome("SharedAlphaCategory", "income_shared")
        }
    }

    @Test
    fun deleteCategory_archivesInUseCategoryWithoutLosingSearchRows() = runTest {
        withFixture {
            insertCategory("used_category", "ArchiveSearchCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("unrelated_category", "UnrelatedSearchCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("expense_used", "used_category", "archive neutral text")
            insertExpense("expense_unrelated", "unrelated_category", "unrelated neutral text")

            categoryRepository.deleteCategory("used_category")

            assertEquals(1L, categoryRepository.getCategoryById("used_category")?.isArchived)
            assertContainsExpense("ArchiveSearchCategory", "expense_used")
            assertContainsExpense("UnrelatedSearchCategory", "expense_unrelated")
        }
    }

    @Test
    fun reassignExpenseCategory_refreshesMovedExpenseSearchRows() = runTest {
        withFixture {
            insertCategory("source_expense", "SourceExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("target_expense", "TargetExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("expense_moved", "source_expense", "moved expense neutral text")

            assertContainsExpense("SourceExpenseCategory", "expense_moved")

            categoryRepository.reassignCategoryTransactions(
                sourceCategoryId = "source_expense",
                targetCategoryId = "target_expense"
            )

            assertContainsExpense("TargetExpenseCategory", "expense_moved")
            assertDoesNotContainExpense("SourceExpenseCategory", "expense_moved")
        }
    }

    @Test
    fun reassignIncomeCategory_refreshesMovedIncomeSearchRows() = runTest {
        withFixture {
            insertCategory("source_income", "SourceIncomeCategory", CATEGORY_TYPE_INCOME)
            insertCategory("target_income", "TargetIncomeCategory", CATEGORY_TYPE_INCOME)
            insertIncome("income_moved", "source_income", "moved income neutral text")

            assertContainsIncome("SourceIncomeCategory", "income_moved")

            categoryRepository.reassignCategoryTransactions(
                sourceCategoryId = "source_income",
                targetCategoryId = "target_income"
            )

            assertContainsIncome("TargetIncomeCategory", "income_moved")
            assertDoesNotContainIncome("SourceIncomeCategory", "income_moved")
        }
    }

    @Test
    fun backupRestore_keepsFullSearchRebuildAfterDataReplacement() = runTest {
        withFixture {
            dataReplacementService.replaceAllData(
                categories = listOf(
                    RestoredCategory(
                        id = "restore_expense_category",
                        name = "RestoreExpenseCategory",
                        icon = "category",
                        categoryType = CATEGORY_TYPE_EXPENSE
                    ),
                    RestoredCategory(
                        id = "restore_income_category",
                        name = "RestoreIncomeCategory",
                        icon = "category",
                        categoryType = CATEGORY_TYPE_INCOME,
                        sortOrder = 1L
                    )
                ),
                expenses = listOf(
                    pendingExpense(
                        id = "restored_expense",
                        categoryId = "restore_expense_category",
                        description = "restored expense neutral text"
                    )
                ),
                incomes = listOf(
                    pendingIncome(
                        id = "restored_income",
                        categoryId = "restore_income_category",
                        description = "restored income neutral text"
                    )
                )
            )

            assertContainsExpense("RestoreExpenseCategory", "restored_expense")
            assertContainsIncome("RestoreIncomeCategory", "restored_income")
        }
    }

    @Test
    fun csvImport_insertsSearchableExpenseAndIncomeRows() = runTest {
        withFixture {
            val csvText = """
                "type";"date";"category";"amount";"description";"shared";"recurring";"recurring_series_id"
                "expense";"2026-01-01";"CsvExpenseCategory";"12.34";"csv expense neutral text";"FALSE";"FALSE";""
                "income";"2026-01-02";"CsvIncomeCategory";"56.78";"csv income neutral text";"FALSE";"FALSE";""
            """.trimIndent()

            val result = importBudgetItemsFromCsv(
                repository = RoomCsvImportStore(this),
                csvText = csvText
            )

            assertEquals(CsvImportResult(importedCount = 2, skippedCount = 0), result)
            assertTrue(searchExpenseIds("CsvExpenseCategory").isNotEmpty())
            assertTrue(searchIncomeIds("CsvIncomeCategory").isNotEmpty())
        }
    }

    private suspend fun TestScope.withFixture(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(this)
        try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }

    private class Fixture(testScope: TestScope) {
        val database: HomeBudgetDatabase = Room.inMemoryDatabaseBuilder<HomeBudgetDatabase>(
            context = ApplicationProvider.getApplicationContext<Context>()
        )
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val transactionRunner = DatabaseTransactionRunner(database)
        val widgetRefreshCoordinator = WidgetRefreshCoordinator(
            coroutineContext = testScope.backgroundScope.coroutineContext,
            debounceMillis = 1L,
            refreshAction = {}
        )
        val categoryRepository = CategoryRepository(database, transactionRunner)
        val expenseEntryRepository = ExpenseEntryRepository(
            database = database,
            transactionRunner = transactionRunner,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
        val incomeRepository = IncomeRepository(
            database = database,
            transactionRunner = transactionRunner,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
        val dataReplacementService = DataReplacementService(
            database = database,
            transactionRunner = transactionRunner,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )

        suspend fun insertCategory(id: String, name: String, type: String) {
            categoryRepository.insertCategory(
                id = id,
                name = name,
                icon = "category",
                color = "#123456",
                categoryType = type
            )
        }

        suspend fun insertExpense(id: String, categoryId: String, description: String) {
            expenseEntryRepository.insertExpenses(
                listOf(
                    pendingExpense(
                        id = id,
                        categoryId = categoryId,
                        description = description
                    )
                )
            )
        }

        suspend fun insertIncome(id: String, categoryId: String, description: String) {
            incomeRepository.insertIncomes(
                listOf(
                    pendingIncome(
                        id = id,
                        categoryId = categoryId,
                        description = description
                    )
                )
            )
        }

        suspend fun searchExpenseIds(query: String): Set<String> =
            expenseEntryRepository.searchExpenseCandidates(query, limit = 20)
                .first()
                .map(Expense::id)
                .toSet()

        suspend fun searchIncomeIds(query: String): Set<String> =
            incomeRepository.searchIncomeCandidates(query, limit = 20)
                .first()
                .map(Income::id)
                .toSet()

        suspend fun assertContainsExpense(query: String, id: String) {
            assertTrue(id in searchExpenseIds(query), "Expected expense $id for query '$query'.")
        }

        suspend fun assertDoesNotContainExpense(query: String, id: String) {
            assertFalse(id in searchExpenseIds(query), "Did not expect expense $id for query '$query'.")
        }

        suspend fun assertContainsIncome(query: String, id: String) {
            assertTrue(id in searchIncomeIds(query), "Expected income $id for query '$query'.")
        }

        suspend fun assertDoesNotContainIncome(query: String, id: String) {
            assertFalse(id in searchIncomeIds(query), "Did not expect income $id for query '$query'.")
        }

        fun close() {
            database.close()
        }
    }

    private class RoomCsvImportStore(
        private val fixture: Fixture
    ) : CsvImportStore {
        override suspend fun seedStarterCategoriesIfEmpty() = Unit

        override suspend fun getAllCategoriesSnapshot(): List<Category> =
            fixture.categoryRepository.getAllCategoriesSnapshot()

        override suspend fun getAllExpensesSnapshot(): List<Expense> =
            fixture.expenseEntryRepository.getAllExpensesSnapshot()

        override suspend fun getAllIncomesSnapshot(): List<Income> =
            fixture.incomeRepository.getAllIncomesSnapshot()

        override suspend fun insertCategory(
            id: String,
            name: String,
            icon: String,
            color: String,
            categoryType: String,
            isArchived: Boolean,
            sortOrder: Long
        ) {
            fixture.categoryRepository.insertCategory(
                id = id,
                name = name,
                icon = icon,
                color = color,
                categoryType = categoryType,
                isArchived = isArchived,
                sortOrder = sortOrder
            )
        }

        override suspend fun insertExpenses(expenses: List<PendingExpense>) {
            fixture.expenseEntryRepository.insertExpenses(expenses)
        }

        override suspend fun insertIncomes(incomes: List<PendingIncome>) {
            fixture.incomeRepository.insertIncomes(incomes)
        }
    }

    private companion object {
        val testDate: Long = LocalDate(2026, 1, 1)
            .atStartOfDayIn(TimeZone.of("UTC"))
            .toEpochMilliseconds()

        fun pendingExpense(
            id: String,
            categoryId: String,
            description: String
        ): PendingExpense = PendingExpense(
            id = id,
            amount = 1234L,
            date = testDate,
            categoryId = categoryId,
            description = description,
            isShared = false
        )

        fun pendingIncome(
            id: String,
            categoryId: String,
            description: String
        ): PendingIncome = PendingIncome(
            id = id,
            amount = 5678L,
            date = testDate,
            description = description,
            categoryId = categoryId
        )
    }
}
