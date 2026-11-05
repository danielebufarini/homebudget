package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.TransactionPageCursor
import it.danielebufarini.spesify.data.TransactionSearchPage
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosTransactionSearchConcurrencyTest {
    @Test
    fun cancelledStart_canBeRetried() = runTest {
        val source = GatedSearchSource().apply { seedFailure = CancellationException("cancelled start") }
        val observer = observer(source)
        assertFailsWith<CancellationException> { observer.start() }
        observer.start()
        assertNotNull(observer.snapshots.value)
        assertEquals(listOf("expense1"), observer.expenseIds())
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun deletingWhilePageIsLoading_cannotResurrectTheDeletedRow() = runTest {
        val source = GatedSearchSource().apply { expenseGate = CompletableDeferred() }
        val observer = observer(source)
        val starting = async { observer.start() }
        source.expenseEntered.await()
        val deleting = async { observer.deleteExpense("expense1") }
        runCurrent()
        source.expenseGate!!.complete(Unit)
        starting.await()
        deleting.await()
        assertTrue(observer.expenseIds().isEmpty())
        assertTrue("expense1" in source.deletedExpenses)
    }

    @Test
    fun cancelledMixedPage_doesNotPublishUncommittedExpenseRowsOnLaterDeletion() = runTest {
        val source = GatedSearchSource().apply { moreExpenses = true; moreIncomes = true }
        val observer = observer(source)
        observer.start()
        source.incomeGate = CompletableDeferred()
        val loading = async { observer.loadMoreResults() }
        source.incomeEntered.await()
        loading.cancelAndJoin()
        observer.deleteExpense("expense1")
        assertTrue(observer.expenseIds().isEmpty())
        source.incomeGate!!.complete(Unit)
        observer.loadMoreResults()
        assertEquals(listOf("expense2"), observer.expenseIds())
    }

    @Test
    fun queuedLoadsAndGrouping_preserveUniqueRowsAndAdvanceBothCursors() = runTest {
        val source = GatedSearchSource().apply { moreExpenses = true; moreIncomes = true }
        val observer = observer(source)
        observer.start()
        val loading = async { observer.loadMoreResults() }
        val grouping = async { observer.setGroupingMode(ExpenseGroupingMode.ByDate) }
        loading.await(); grouping.await()
        observer.loadMoreResults()
        assertEquals(setOf("expense1", "expense2"), observer.expenseIds().toSet())
        assertEquals(2, observer.expenseIds().size)
        assertEquals(2, observer.snapshots.value!!.incomeSnapshot.sections.sumOf { it.rows.size })
        assertEquals(false, observer.snapshots.value!!.canLoadMoreExpenseResults)
        assertEquals(false, observer.snapshots.value!!.canLoadMoreIncomeResults)
    }
}

private fun IosTransactionSearchObserver.expenseIds() = snapshots.value?.expenseSnapshot?.sections.orEmpty().flatMap { it.rows.map { row -> row.id } }
private fun observer(source: IosTransactionSearchDataSource) = IosTransactionSearchObserver("query", ExpenseGroupingMode.ByCategory, source) {
    IosGroupedLocalization(
        currencySymbol = "€", expense = "Expense", income = "Income", sharedExpense = "Shared", category = "Category",
        noExpensesForDay = "Empty", noExpensesForMonth = "Empty", noSharedExpensesForMonth = "Empty",
        noIncomeForMonth = "Empty", noSearchResults = "Empty", unknownCategory = "Unknown",
        noExpensesForCategoryThisMonthTemplate = "Empty %s", shortMonthNames = List(12) { "M${it + 1}" },
        resolveCategoryName = { _, name -> name }
    )
}

private class GatedSearchSource : IosTransactionSearchDataSource {
    var seedFailure: Throwable? = null
    var expenseGate: CompletableDeferred<Unit>? = null
    val expenseEntered = CompletableDeferred<Unit>()
    var incomeGate: CompletableDeferred<Unit>? = null
    val incomeEntered = CompletableDeferred<Unit>()
    var moreExpenses = false
    var moreIncomes = false
    val deletedExpenses = mutableSetOf<String>()
    override suspend fun seedCategories() { seedFailure?.also { seedFailure = null; throw it } }
    override suspend fun categories() = listOf(Category(id = "food", name = "Food", icon = "category"))
    override suspend fun expenses(query: String, cursor: TransactionPageCursor?): TransactionSearchPage<Expense> {
        val id = if (cursor == null) "expense1" else "expense2"
        val rows = if (id in deletedExpenses) emptyList() else listOf(Expense(
            id = id, amount = 1234L, date = 1767225600000L, categoryId = "food", description = "query"
        ))
        expenseEntered.complete(Unit)
        expenseGate?.await()
        return TransactionSearchPage(rows, rows.lastOrNull()?.let { TransactionPageCursor(it.date, it.id) }, cursor == null && moreExpenses)
    }
    override suspend fun incomes(query: String, cursor: TransactionPageCursor?): TransactionSearchPage<Income> {
        val rows = if (moreIncomes) listOf(Income(
            id = if (cursor == null) "income1" else "income2", amount = 5678L, date = 1767225600000L, description = "query"
        )) else emptyList()
        if (cursor != null) { incomeEntered.complete(Unit); incomeGate?.await() }
        return TransactionSearchPage(rows, rows.lastOrNull()?.let { TransactionPageCursor(it.date, it.id) }, cursor == null && moreIncomes)
    }
    override suspend fun deleteExpense(id: String) { deletedExpenses += id }
    override suspend fun deleteIncome(id: String) = Unit
    override suspend fun deleteExpenseSeries(id: String) = Unit
    override suspend fun deleteIncomeSeries(id: String) = Unit
}
