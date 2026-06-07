package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.data.csv.CsvImportResult
import it.danielebufarini.spesify.data.csv.importBudgetItemsFromCsv
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

    @Test
    fun expenseSearchCandidatePage_usesStableKeysetPaginationWithoutOverlap() = runTest {
        withFixture {
            insertCategory("expense_keyset_category", "KeysetExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense(
                id = "expense_b",
                categoryId = "expense_keyset_category",
                description = "keyset expense shared token",
                date = millisForDate(2026, 1, 3)
            )
            insertExpense(
                id = "expense_a",
                categoryId = "expense_keyset_category",
                description = "keyset expense shared token",
                date = millisForDate(2026, 1, 3)
            )
            insertExpense(
                id = "expense_c",
                categoryId = "expense_keyset_category",
                description = "keyset expense shared token",
                date = millisForDate(2026, 1, 2)
            )
            insertExpense(
                id = "expense_d",
                categoryId = "expense_keyset_category",
                description = "keyset expense shared token",
                date = millisForDate(2026, 1, 1)
            )

            val firstPage = expenseEntryRepository.searchExpenseCandidatePage(
                query = "keyset",
                limit = 2,
                cursor = null
            ).first()
            val secondPage = expenseEntryRepository.searchExpenseCandidatePage(
                query = "keyset",
                limit = 2,
                cursor = firstPage.nextCursor
            ).first()

            assertEquals(listOf("expense_a", "expense_b"), firstPage.items.map(Expense::id))
            assertTrue(firstPage.canLoadMore)
            assertEquals(listOf("expense_c", "expense_d"), secondPage.items.map(Expense::id))
            assertFalse(secondPage.canLoadMore)
            assertEquals(
                4,
                (firstPage.items + secondPage.items)
                    .map(Expense::id)
                    .distinct()
                    .size
            )
        }
    }

    @Test
    fun incomeSearchCandidatePage_usesStableKeysetPaginationWithoutOverlap() = runTest {
        withFixture {
            insertCategory("income_keyset_category", "KeysetIncomeCategory", CATEGORY_TYPE_INCOME)
            insertIncome(
                id = "income_b",
                categoryId = "income_keyset_category",
                description = "keyset income shared token",
                date = millisForDate(2026, 2, 3)
            )
            insertIncome(
                id = "income_a",
                categoryId = "income_keyset_category",
                description = "keyset income shared token",
                date = millisForDate(2026, 2, 3)
            )
            insertIncome(
                id = "income_c",
                categoryId = "income_keyset_category",
                description = "keyset income shared token",
                date = millisForDate(2026, 2, 2)
            )

            val firstPage = incomeRepository.searchIncomeCandidatePage(
                query = "keyset",
                limit = 2,
                cursor = null
            ).first()
            val secondPage = incomeRepository.searchIncomeCandidatePage(
                query = "keyset",
                limit = 2,
                cursor = firstPage.nextCursor
            ).first()

            assertEquals(listOf("income_a", "income_b"), firstPage.items.map(Income::id))
            assertTrue(firstPage.canLoadMore)
            assertEquals(listOf("income_c"), secondPage.items.map(Income::id))
            assertFalse(secondPage.canLoadMore)
        }
    }

}