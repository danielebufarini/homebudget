package it.danielebufarini.spesify.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransactionEditorServiceTest {
    @Test
    fun installments_keepEveryCentAndClampMonthEndDates() = runTest {
        val writes = EditorRecordingRepository()
        val service = editorService(writes)
        val result = service.execute(expense(EditorItem.New()).copy(
            amount = 1001L, schedule = ExpenseSchedule.Installments(3)
        ))
        assertIs<EditorOperationResult.Saved>(result)
        assertEquals(listOf(334L, 334L, 333L), writes.expenses.map { it.amount })
        assertEquals(listOf(day(2026, 1, 31), day(2026, 2, 28), day(2026, 3, 31)), writes.expenses.map { it.date })
        assertEquals(writes.expenses.map { it.id }, result.transactionIds)
        assertTrue(writes.expenses.all { it.categoryId == "archived-selected-category" && it.isShared })
        assertTrue(writes.expenses.all { it.description == "  note  " && it.recurringSeriesId == null })
    }

    @Test
    fun singleCreation_keepsIngressIdDateAndDescriptionPolicy() = runTest {
        val writes = EditorRecordingRepository()
        val command = expense(EditorItem.New("voice-expense-explicit")).copy(date = date + 12345L)
        editorService(writes).execute(command).requireSuccess()
        assertEquals(PendingExpense("voice-expense-explicit", 1001L, command.date,
            "archived-selected-category", "  note  ", true), writes.expenses.single())
    }

    @Test
    fun convertingExpenseToMonthly_keepsAnchorAndCreatesOneSeries() = runTest {
        val writes = EditorRecordingRepository()
        editorService(writes).execute(expense(EditorItem.Existing("anchor")).copy(
            schedule = ExpenseSchedule.Monthly
        )).requireSuccess()
        assertEquals(RECURRING_MONTHLY_OCCURRENCES, writes.expenses.size)
        assertEquals("anchor", writes.expenses.first().id)
        assertEquals(writes.expenses.size, writes.expenses.map { it.id }.distinct().size)
        assertEquals(1, writes.expenses.map { it.recurringSeriesId }.distinct().size)
        assertTrue(writes.expenses.all { it.recurringSeriesId!!.startsWith("recurring-expense-") })
        assertTrue(writes.expenses.all { it.amount == 1001L && it.description == "  note  " })
    }

    @Test
    fun editingOneExpense_keepsSeriesIdentityWithoutChangingOtherOccurrences() = runTest {
        val writes = EditorRecordingRepository()
        editorService(writes).execute(expense(EditorItem.Existing("anchor", "existing-series")).copy(
            schedule = ExpenseSchedule.Monthly
        )).requireSuccess()
        assertEquals("existing-series", writes.expenses.single().recurringSeriesId)
        assertEquals("anchor", writes.expenses.single().id)
        assertTrue(writes.expenseSeriesUpdates.isEmpty())
    }

    @Test
    fun editingExpenseSeries_usesSeriesOperationAndReturnsAnchor() = runTest {
        val writes = EditorRecordingRepository()
        val command = expense(EditorItem.Existing("anchor", "existing-series")).copy(scope = EditorMutationScope.Series)
        val result = editorService(writes).execute(command)
        assertEquals(EditorOperationResult.Saved(listOf("anchor")), result)
        assertEquals(listOf(command), writes.expenseSeriesUpdates)
        assertTrue(writes.expenses.isEmpty())
    }

    @Test
    fun incomeConversion_keepsAnchorCategoryAndMonthlyAmounts() = runTest {
        val writes = EditorRecordingRepository()
        editorService(writes).execute(SaveIncomeCommand(
            EditorItem.Existing("income-anchor"), 3456L, date, null, "  salary  ", recurringMonthly = true
        )).requireSuccess()
        assertEquals(RECURRING_MONTHLY_OCCURRENCES, writes.incomes.size)
        assertEquals("income-anchor", writes.incomes.first().id)
        assertEquals(writes.incomes.size, writes.incomes.map { it.id }.distinct().size)
        assertTrue(writes.incomes.all { it.amount == 3456L && it.categoryId == null && it.description == "  salary  " })
        assertEquals(1, writes.incomes.map { it.recurringSeriesId }.distinct().size)
        assertTrue(writes.incomes.first().recurringSeriesId!!.startsWith("recurring-income-"))
    }

    @Test
    fun editingIncome_scopeDistinguishesSingleOccurrenceAndWholeSeries() = runTest {
        val writes = EditorRecordingRepository()
        val service = editorService(writes)
        val single = SaveIncomeCommand(EditorItem.Existing("income", "series"), 3456L, date, "salary", null)
        service.execute(single).requireSuccess()
        assertEquals("series", writes.incomes.single().recurringSeriesId)
        assertTrue(writes.incomeSeriesUpdates.isEmpty())
        val series = single.copy(scope = EditorMutationScope.Series)
        service.execute(series).requireSuccess()
        assertEquals(listOf(series), writes.incomeSeriesUpdates)
        assertEquals(1, writes.incomes.size)
    }

    @Test
    fun delete_routesSingleAndSeriesForBothKinds() = runTest {
        val writes = EditorRecordingRepository()
        val service = editorService(writes)
        val commands = listOf(
            DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Single("expense")),
            DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Series("expense-series")),
            DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Single("income")),
            DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Series("income-series"))
        )
        commands.forEach { assertEquals(EditorOperationResult.Deleted, service.execute(it)) }
        assertEquals(commands, writes.deletions)
    }

    @Test
    fun invalidInstallments_returnFailureBeforeAnyWrite() = runTest {
        val writes = EditorRecordingRepository()
        val result = editorService(writes).execute(expense(EditorItem.New()).copy(schedule = ExpenseSchedule.Installments(0)))
        assertIs<EditorOperationResult.Failed>(result)
        assertIs<IllegalArgumentException>(result.cause)
        assertTrue(writes.expenses.isEmpty())
    }

    @Test
    fun storageFailure_preservesCauseAndCancellationEscapes() = runTest {
        val writes = EditorRecordingRepository()
        val service = editorService(writes)
        val error = IllegalStateException("storage failure")
        writes.failure = error
        val result = service.execute(expense(EditorItem.New()))
        assertIs<EditorOperationResult.Failed>(result)
        assertSame(error, result.cause)
        assertSame(error, assertFailsWith<IllegalStateException> { result.requireSuccess() })
        val cancellation = CancellationException("caller cancelled")
        writes.failure = cancellation
        assertSame(cancellation, assertFailsWith<CancellationException> { service.execute(expense(EditorItem.New())) })
    }
}

private fun editorService(writes: TransactionWriteRepository): TransactionEditorService {
    var next = 0
    return TransactionEditorService(writes) { prefix -> "$prefix-${++next}" }
}

private val date get() = day(2026, 1, 31)
private fun day(year: Int, month: Int, day: Int) = LocalDate(year, month, day)
    .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
private fun expense(item: EditorItem) = SaveExpenseCommand(item, 1001L, date, "archived-selected-category", "  note  ", true)

internal class EditorRecordingRepository : TransactionWriteRepository {
    val expenses = mutableListOf<PendingExpense>()
    val incomes = mutableListOf<PendingIncome>()
    val expenseSeriesUpdates = mutableListOf<SaveExpenseCommand>()
    val incomeSeriesUpdates = mutableListOf<SaveIncomeCommand>()
    val deletions = mutableListOf<DeleteTransactionCommand>()
    var failure: Throwable? = null
    override suspend fun insertExpenses(expenses: List<PendingExpense>) { failure?.let { throw it }; this.expenses += expenses }
    override suspend fun insertIncomes(incomes: List<PendingIncome>) { failure?.let { throw it }; this.incomes += incomes }
    override suspend fun insertIncome(id: String, amount: Long, date: Long, description: String?, categoryId: String?, recurringSeriesId: String?) {
        insertIncomes(listOf(PendingIncome(id, amount, date, description, recurringSeriesId, categoryId)))
    }
    override suspend fun updateRecurringExpenseSeries(anchorExpenseId: String, seriesId: String, amount: Long, date: Long, categoryId: String, description: String?, isShared: Boolean) {
        expenseSeriesUpdates += SaveExpenseCommand(EditorItem.Existing(anchorExpenseId, seriesId), amount, date, categoryId, description, isShared, scope = EditorMutationScope.Series)
    }
    override suspend fun updateRecurringIncomeSeries(anchorIncomeId: String, seriesId: String, amount: Long, date: Long, description: String?, categoryId: String?) {
        incomeSeriesUpdates += SaveIncomeCommand(EditorItem.Existing(anchorIncomeId, seriesId), amount, date, categoryId, description, scope = EditorMutationScope.Series)
    }
    override suspend fun deleteExpense(id: String) { deletions += DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Single(id)) }
    override suspend fun deleteRecurringExpenseSeries(seriesId: String) { deletions += DeleteTransactionCommand(TransactionKind.Expense, EditorDeletion.Series(seriesId)) }
    override suspend fun deleteIncome(id: String) { deletions += DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Single(id)) }
    override suspend fun deleteRecurringIncomeSeries(seriesId: String) { deletions += DeleteTransactionCommand(TransactionKind.Income, EditorDeletion.Series(seriesId)) }
}
