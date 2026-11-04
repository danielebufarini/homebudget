package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.di.sharedModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RepositoryAdapterIntegrationTest {
    @Test
    fun categoryPatch_preservesFieldsAndSearchForAdapterAndFacade() = runTest {
        withFixture {
            val adapters = adapters()
            insertCategory("income", "OriginalIncomeCategory", CATEGORY_TYPE_INCOME)
            insertIncome("salary", "income", "monthly salary")

            adapters.categories.updateCategory("income", "RenamedIncomeCategory", "work", null, null)

            val updated = adapters.facade.getAllCategoriesSnapshot().single()
            assertEquals("#123456", updated.color)
            assertEquals(CATEGORY_TYPE_INCOME, updated.categoryType)
            assertEquals("work", updated.icon)
            assertContainsIncome("RenamedIncomeCategory", "salary")
            assertDoesNotContainIncome("OriginalIncomeCategory", "salary")

            adapters.facade.updateCategory("income", "FinalIncomeCategory", "payments", color = "#ABCDEF")
            val afterFacadeUpdate = adapters.categories.getAllCategoriesSnapshot().single()
            assertEquals("#ABCDEF", afterFacadeUpdate.color)
            assertEquals(CATEGORY_TYPE_INCOME, afterFacadeUpdate.categoryType)
            assertContainsIncome("FinalIncomeCategory", "salary")
            assertDoesNotContainIncome("RenamedIncomeCategory", "salary")
        }
    }

    @Test
    fun missingCategory_preservesTheFacadeFailureContract() = runTest {
        withFixture {
            val adapters = adapters()
            val adapterError = assertFailsWith<IllegalStateException> {
                adapters.categories.updateCategory("missing", "Name", "category", null, null)
            }
            val facadeError = assertFailsWith<IllegalStateException> {
                adapters.facade.updateCategory("missing", "Name", "category")
            }
            assertEquals("Category missing not found", adapterError.message)
            assertEquals(facadeError.message, adapterError.message)
            assertTrue(adapters.categories.getAllCategoriesSnapshot().isEmpty())
        }
    }

    @Test
    fun adapterWrites_areVisibleToReadersAndFacadeAndDeleteSearchRows() = runTest {
        withFixture {
            val adapters = adapters()
            insertCategory("expense", "DailyExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("income", "WorkIncomeCategory", CATEGORY_TYPE_INCOME)

            adapters.writes.insertExpenses(listOf(pendingExpense("expense_1", "expense", "groceries")))
            adapters.writes.insertIncome("income_1", 4567L, testDate, "salary", "income", null)
            adapters.writes.insertIncomes(listOf(pendingIncome("income_2", "income", "bonus")))

            assertEquals(1234L, adapters.expenses.getExpenseById("expense_1")?.amount)
            assertEquals(4567L, adapters.incomes.getIncomeById("income_1")?.amount)
            assertEquals(setOf("income_1", "income_2"), adapters.incomes.getAllIncomes().first().map { it.id }.toSet())
            assertEquals(adapters.facade.getAllExpensesSnapshot(), adapters.expenses.getAllExpensesSnapshot())
            assertEquals(adapters.facade.getAllIncomesSnapshot(), adapters.incomes.getAllIncomesSnapshot())
            assertEquals(
                listOf("expense_1"),
                adapters.expenses.searchExpenseCandidatePage("groceries", 10, null).first().items.map { it.id }
            )
            assertEquals(
                listOf("income_1"),
                adapters.incomes.searchIncomeCandidatePage("salary", 10, null).first().items.map { it.id }
            )

            adapters.writes.deleteExpense("expense_1")
            adapters.writes.deleteIncome("income_1")
            assertNull(adapters.facade.getExpenseById("expense_1"))
            assertNull(adapters.facade.getIncomeById("income_1"))
            assertDoesNotContainExpense("groceries", "expense_1")
            assertDoesNotContainIncome("salary", "income_1")
            assertEquals(listOf("income_2"), adapters.incomes.getAllIncomesSnapshot().map { it.id })
        }
    }

    @Test
    fun categoryReassignmentAndArchive_keepTransactionsAndSearchConsistent() = runTest {
        withFixture {
            val adapters = adapters()
            insertCategory("source", "SourceCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("target", "TargetCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("expense", "source", "lunch")

            adapters.categories.deleteCategory("source")
            assertEquals(1L, adapters.facade.getAllCategoriesSnapshot().single { it.id == "source" }.isArchived)
            assertTrue(adapters.categories.isCategoryInUse("source"))

            adapters.categories.reassignCategoryTransactions("source", "target")
            assertEquals("target", adapters.facade.getExpenseById("expense")?.categoryId)
            assertFalse(adapters.categories.isCategoryInUse("source"))
            assertEquals(listOf("target"), adapters.categories.getAllCategoriesSnapshot().map { it.id })
            assertContainsExpense("TargetCategory", "expense")
            assertDoesNotContainExpense("SourceCategory", "expense")
        }
    }

    @Test
    fun seriesUpdatesAndDeletes_keepRulesAndSearchInSync() = runTest {
        withFixture {
            val adapters = adapters()
            insertCategory("expense", "RecurringExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("income", "RecurringIncomeCategory", CATEGORY_TYPE_INCOME)
            adapters.writes.insertExpenses(
                listOf(pendingExpense("expense_anchor", "expense", "oldexpense").copy(recurringSeriesId = "expense_series"))
            )
            adapters.writes.insertIncomes(
                listOf(pendingIncome("income_anchor", "income", "oldincome").copy(recurringSeriesId = "income_series"))
            )

            adapters.writes.updateRecurringExpenseSeries(
                "expense_anchor", "expense_series", 9876L, testDate, "expense", "newexpense", true
            )
            adapters.writes.updateRecurringIncomeSeries(
                "income_anchor", "income_series", 8765L, testDate, "newincome", "income"
            )

            assertEquals(9876L, adapters.facade.getExpenseById("expense_anchor")?.amount)
            assertEquals(1L, adapters.facade.getExpenseById("expense_anchor")?.isShared)
            assertEquals(8765L, adapters.facade.getIncomeById("income_anchor")?.amount)
            assertEquals(9876L, database.recurringTransactionRuleDao().getRuleById("expense_series")?.amount)
            assertEquals(8765L, database.recurringTransactionRuleDao().getRuleById("income_series")?.amount)
            assertEquals(
                adapters.expenses.getAllExpensesSnapshot().map { it.id }.toSet(),
                collectAllPages { cursor ->
                    adapters.expenses.searchExpenseCandidatePage("newexpense", 5, cursor).first()
                }.map { it.id }.toSet()
            )
            assertDoesNotContainExpense("oldexpense", "expense_anchor")
            assertEquals(
                adapters.incomes.getAllIncomesSnapshot().map { it.id }.toSet(),
                collectAllPages { cursor ->
                    adapters.incomes.searchIncomeCandidatePage("newincome", 5, cursor).first()
                }.map { it.id }.toSet()
            )
            assertDoesNotContainIncome("oldincome", "income_anchor")

            adapters.writes.deleteRecurringExpenseSeries("expense_series")
            adapters.writes.deleteRecurringIncomeSeries("income_series")
            assertTrue(adapters.expenses.getAllExpensesSnapshot().isEmpty())
            assertTrue(adapters.incomes.getAllIncomesSnapshot().isEmpty())
            assertTrue(database.recurringTransactionRuleDao().getAllRulesSnapshot().isEmpty())
            assertDoesNotContainExpense("newexpense", "expense_anchor")
            assertDoesNotContainIncome("newincome", "income_anchor")
        }
    }

    @Test
    fun dashboardAdapter_matchesFacadeForTotalsTrendsAndRecentTransactions() = runTest {
        withFixture {
            val adapters = adapters()
            insertCategory("expense", "ExpenseCategory", CATEGORY_TYPE_EXPENSE)
            insertCategory("income", "IncomeCategory", CATEGORY_TYPE_INCOME)
            insertExpense("expense", "expense", "expense")
            insertIncome("income", "income", "income")

            val summary = adapters.dashboard.getDashboardMonthSummary(2026, 1).first()
            assertEquals(1234L, summary.totalAmount)
            assertEquals(5678L, summary.incomeAmount)
            assertEquals(1, summary.expenseCount)
            assertEquals(adapters.facade.getDashboardMonthSummary(2026, 1).first(), summary)
            assertEquals(
                adapters.facade.getDashboardBalanceTrend(2026, 1, 6).first(),
                adapters.dashboard.getDashboardBalanceTrend(2026, 1, 6).first()
            )
            assertEquals(
                adapters.facade.getDashboardRecentTransactions(1).first(),
                adapters.dashboard.getDashboardRecentTransactions(1).first()
            )
            assertEquals(1, adapters.dashboard.getDashboardRecentTransactions(1).first().size)
            assertEquals(
                adapters.facade.getRecurringExpenseOverviewForMonth(2026, 1).first(),
                adapters.dashboard.getRecurringExpenseOverviewForMonth(2026, 1).first()
            )
            assertEquals(WidgetMonthSummary(1234L, 5678L), adapters.dashboard.getWidgetMonthSummary(2026, 1))
        }
    }

    @Test
    fun dataReplacement_rebuildsSearchAndRefreshesOnlyAfterCommittedRowsAreVisible() = runTest {
        withFixture {
            insertCategory("old", "OldCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("old_expense", "old", "oldexpense")
            var refreshedExpenseIds: List<String>? = null
            val refreshCoordinator = WidgetRefreshCoordinator(
                coroutineContext = backgroundScope.coroutineContext,
                refreshAction = {
                    refreshedExpenseIds = runBlocking { expenseEntryRepository.getAllExpensesSnapshot().map { it.id } }
                }
            )
            val replacement = BudgetDataReplacementRepositoryAdapter(
                DataReplacementService(database, transactionRunner, refreshCoordinator)
            )

            replacement.replaceAllData(
                listOf(RestoredCategory("new", "NewCategory", "category")),
                listOf(pendingExpense("new_expense", "new", "newexpense")),
                emptyList()
            )

            assertEquals(listOf("new_expense"), refreshedExpenseIds)
            assertContainsExpense("NewCategory", "new_expense")
            assertDoesNotContainExpense("oldexpense", "old_expense")
            assertEquals(listOf("new"), categoryRepository.getAllCategoriesSnapshot().map { it.id })
        }
    }

    @Test
    fun invalidDataReplacement_rollsBackAllTablesAndDoesNotRefreshWidgets() = runTest {
        withFixture {
            insertCategory("old", "RetainedCategory", CATEGORY_TYPE_EXPENSE)
            insertExpense("retained_expense", "old", "retainedexpense")
            var refreshCount = 0
            val refreshCoordinator = WidgetRefreshCoordinator(
                coroutineContext = backgroundScope.coroutineContext,
                refreshAction = { refreshCount++ }
            )
            val replacement = BudgetDataReplacementRepositoryAdapter(
                DataReplacementService(database, transactionRunner, refreshCoordinator)
            )

            val failure = assertFails {
                replacement.replaceAllData(
                    listOf(RestoredCategory("new", "NewCategory", "category")),
                    listOf(pendingExpense("invalid", "missing_category", "invalidexpense")),
                    emptyList()
                )
            }

            assertTrue(failure.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true), failure.toString())
            assertEquals(0, refreshCount)
            assertEquals(listOf("old"), categoryRepository.getAllCategoriesSnapshot().map { it.id })
            assertEquals(listOf("retained_expense"), expenseEntryRepository.getAllExpensesSnapshot().map { it.id })
            assertContainsExpense("RetainedCategory", "retained_expense")
        }
    }

    @Test
    fun sharedComposition_resolvesWorkingContractsWithoutConstructingTheFacade() = runTest {
        withFixture {
            val fixture = this
            val application = koinApplication {
                modules(
                    sharedModule,
                    module {
                        single<SpesifyDatabase> { fixture.database }
                        single { fixture.widgetRefreshCoordinator }
                        single<ExpenseRepository> { error("The specialized contracts must not resolve the compatibility Facade") }
                    }
                )
            }
            try {
                val koin = application.koin
                val categories = koin.get<CategoryManagementRepository>()
                categories.insertCategory("expense", "DirectCategory", "category")
                koin.get<TransactionWriteRepository>().insertExpenses(
                    listOf(pendingExpense("direct_expense", "expense", "directexpense"))
                )
                assertEquals("direct_expense", koin.get<ExpenseReadRepository>().getAllExpensesSnapshot().single().id)
                assertEquals(1234L, koin.get<DashboardReadRepository>().getWidgetMonthSummary(2026, 1).expenseAmount)
                assertTrue(koin.get<IncomeReadRepository>().getAllIncomesSnapshot().isEmpty())
                koin.get<BudgetDataReplacementRepository>().replaceAllData(emptyList(), emptyList(), emptyList())
                assertTrue(categories.getAllCategoriesSnapshot().isEmpty())
            } finally {
                application.close()
            }
        }
    }
}

private class RepositoryAdapters(fixture: CategorySearchIndexFixture) {
    private val recurringTransactions = RecurringTransactionService(
        fixture.database, fixture.transactionRunner, fixture.widgetRefreshCoordinator
    )
    private val dashboardRepository = DashboardRepository(fixture.database)
    val facade = ExpenseRepository(
        fixture.categoryRepository, fixture.expenseEntryRepository, fixture.incomeRepository,
        dashboardRepository, recurringTransactions, fixture.dataReplacementService
    )
    val categories: CategoryManagementRepository = CategoryManagementRepositoryAdapter(fixture.categoryRepository)
    val expenses: ExpenseReadRepository = ExpenseReadRepositoryAdapter(fixture.expenseEntryRepository)
    val incomes: IncomeReadRepository = IncomeReadRepositoryAdapter(fixture.incomeRepository)
    val dashboard: DashboardReadRepository = DashboardReadRepositoryAdapter(dashboardRepository)
    val writes: TransactionWriteRepository = TransactionWriteRepositoryAdapter(
        fixture.expenseEntryRepository, fixture.incomeRepository, recurringTransactions
    )
}

private fun CategorySearchIndexFixture.adapters() = RepositoryAdapters(this)

private suspend fun <T> collectAllPages(
    loadPage: suspend (TransactionPageCursor?) -> TransactionSearchPage<T>
): List<T> {
    val items = mutableListOf<T>()
    val visitedCursors = mutableSetOf<TransactionPageCursor>()
    var cursor: TransactionPageCursor? = null
    do {
        val page = loadPage(cursor)
        items.addAll(page.items)
        cursor = if (page.canLoadMore) {
            val nextCursor = page.nextCursor
            assertTrue(nextCursor != null && visitedCursors.add(nextCursor), "Pagination must advance")
            nextCursor
        } else {
            null
        }
    } while (cursor != null)
    return items
}
