package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionEditorPersistenceTest {
    @Test
    fun expenseEditor_persistsInstallmentsAndSingleSeriesEditsWithSearchUpdates() = runTest {
        withFixture {
            insertCategory("food", "ArchivedFood", CATEGORY_TYPE_EXPENSE)
            categoryRepository.setCategoryArchived("food", true)
            val service = editorService()
            service.execute(SaveExpenseCommand(EditorItem.New(), 1001L, testDate, "food", "  installment  ", false,
                ExpenseSchedule.Installments(3))).requireSuccess()
            val installments = expenseEntryRepository.getAllExpensesSnapshot()
            assertEquals(1001L, installments.sumOf { it.amount })
            assertEquals(listOf(333L, 334L, 334L), installments.map { it.amount }.sorted())
            assertEquals(3, searchExpenseIds("installment").size)
            service.execute(DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Single(installments.first().id))).requireSuccess()
            assertEquals(2, expenseEntryRepository.getAllExpensesSnapshot().size)

            val saved = service.execute(SaveExpenseCommand(EditorItem.New("anchor"), 1234L, testDate, "food", "oldseries", false,
                ExpenseSchedule.Monthly)) as EditorOperationResult.Saved
            val anchor = expenseEntryRepository.getExpenseById("anchor")!!
            val seriesId = anchor.recurringSeriesId!!
            val otherId = saved.transactionIds.first { it != "anchor" }
            service.execute(SaveExpenseCommand(EditorItem.Existing("anchor", seriesId), 5678L, testDate, "food", "singleedit", true)).requireSuccess()
            assertEquals(5678L, expenseEntryRepository.getExpenseById("anchor")?.amount)
            assertEquals(1234L, expenseEntryRepository.getExpenseById(otherId)?.amount)
            assertEquals(1234L, database.recurringTransactionRuleDao().getRuleById(seriesId)?.amount)
            assertContainsExpense("singleedit", "anchor")
            assertDoesNotContainExpense("oldseries", "anchor")

            service.execute(DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Series(seriesId))).requireSuccess()
            assertEquals(2, expenseEntryRepository.getAllExpensesSnapshot().size)
            assertFalse("anchor" in searchExpenseIds("singleedit"))
            assertTrue(database.recurringTransactionRuleDao().getAllRulesSnapshot().isEmpty())
        }
    }

    @Test
    fun incomeEditor_convertsExistingThenUpdatesAndDeletesTheWholeSeries() = runTest {
        withFixture {
            insertCategory("salary", "Salary", CATEGORY_TYPE_INCOME)
            insertIncome("anchor", "salary", "oldsalary")
            val service = editorService()
            service.execute(SaveIncomeCommand(EditorItem.Existing("anchor"), 7777L, testDate, "salary", "salaryseries", true)).requireSuccess()
            val converted = incomeRepository.getAllIncomesSnapshot()
            assertEquals(RECURRING_MONTHLY_OCCURRENCES, converted.size)
            val seriesId = incomeRepository.getIncomeById("anchor")!!.recurringSeriesId!!
            service.execute(SaveIncomeCommand(EditorItem.Existing("anchor", seriesId), 8888L, testDate,
                "salary", "updatedsalary", scope = EditorMutationScope.Series)).requireSuccess()
            assertTrue(incomeRepository.getAllIncomesSnapshot().all { it.amount == 8888L && it.description == "updatedsalary" })
            assertEquals(8888L, database.recurringTransactionRuleDao().getRuleById(seriesId)?.amount)
            val allIncomes = incomeRepository.getAllIncomesSnapshot()
            assertEquals(
                allIncomes.map { it.id }.toSet(),
                incomeRepository.searchIncomeCandidates("updatedsalary", allIncomes.size).first().map { it.id }.toSet()
            )
            assertDoesNotContainIncome("oldsalary", "anchor")
            service.execute(DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Series(seriesId))).requireSuccess()
            assertTrue(incomeRepository.getAllIncomesSnapshot().isEmpty())
            assertTrue(searchIncomeIds("updatedsalary").isEmpty())
            assertTrue(database.recurringTransactionRuleDao().getAllRulesSnapshot().isEmpty())
        }
    }
}

private fun CategorySearchIndexFixture.editorService() = TransactionEditorService(TransactionWriteRepositoryAdapter(
    expenseEntryRepository, incomeRepository, RecurringTransactionService(database, transactionRunner, widgetRefreshCoordinator)
))
