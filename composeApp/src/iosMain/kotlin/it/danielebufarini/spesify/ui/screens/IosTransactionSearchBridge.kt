package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.TransactionPageCursor
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.data.sumAmountOf
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosTransactionSearchSnapshot(
    val expenseSnapshot: IosGroupedExpensesSnapshot,
    val incomeSnapshot: IosMonthlyIncomesSnapshot,
    val canLoadMoreExpenseResults: Boolean,
    val canLoadMoreIncomeResults: Boolean
)

class IosTransactionSearchObserver internal constructor(
    private val query: String,
    initialGroupingMode: ExpenseGroupingMode,
    private val source: IosTransactionSearchDataSource,
    private val loadLocalization: suspend () -> IosGroupedLocalization
) {
    constructor(query: String, initialGroupingMode: ExpenseGroupingMode) : this(
        query, initialGroupingMode, RepositoryIosTransactionSearchDataSource(), ::loadIosGroupedLocalization
    )
    private val mutationMutex = Mutex()
    private val snapshotState = MutableStateFlow<IosTransactionSearchSnapshot?>(null)
    private var groupingMode = initialGroupingMode
    private var expenseCursor: TransactionPageCursor? = null
    private var incomeCursor: TransactionPageCursor? = null
    private var hasLoadedExpensePage = false
    private var hasLoadedIncomePage = false
    private var loadedExpenses: List<Expense> = emptyList()
    private var loadedIncomes: List<Income> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var localization: IosGroupedLocalization? = null
    private var canLoadMoreExpenseResults = false
    private var canLoadMoreIncomeResults = false
    private var hasStarted = false

    val snapshots: StateFlow<IosTransactionSearchSnapshot?> = snapshotState.asStateFlow()

    suspend fun start() = mutationMutex.withLock {
        if (hasStarted) {
            return@withLock
        }
        source.seedCategories()
        localization = loadLocalization()
        latestCategories = source.categories()
        loadNextPage()
        hasStarted = true
    }

    suspend fun setGroupingMode(groupingMode: ExpenseGroupingMode) = mutationMutex.withLock {
        if (this.groupingMode != groupingMode) {
            this.groupingMode = groupingMode
            publishSnapshot()
        }
    }

    suspend fun loadMoreResults() = mutationMutex.withLock {
        if (hasStarted) loadNextPage()
    }

    suspend fun deleteExpense(id: String) = mutationMutex.withLock {
        source.deleteExpense(id)
        loadedExpenses = loadedExpenses.filterNot { it.id == id }
        publishSnapshot()
    }

    suspend fun deleteIncome(id: String) = mutationMutex.withLock {
        source.deleteIncome(id)
        loadedIncomes = loadedIncomes.filterNot { it.id == id }
        publishSnapshot()
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) = mutationMutex.withLock {
        source.deleteExpenseSeries(seriesId)
        loadedExpenses = loadedExpenses.filterNot { it.recurringSeriesId == seriesId }
        publishSnapshot()
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) = mutationMutex.withLock {
        source.deleteIncomeSeries(seriesId)
        loadedIncomes = loadedIncomes.filterNot { it.recurringSeriesId == seriesId }
        publishSnapshot()
    }

    private suspend fun loadNextPage() {
        val shouldLoadExpenses = !hasLoadedExpensePage || canLoadMoreExpenseResults
        val shouldLoadIncomes = !hasLoadedIncomePage || canLoadMoreIncomeResults
        if (!shouldLoadExpenses && !shouldLoadIncomes) return

        // Fetch before committing either cursor: a cancelled mixed page must not expose half its rows.
        val expensePage = if (shouldLoadExpenses) source.expenses(query, expenseCursor) else null
        val incomePage = if (shouldLoadIncomes) source.incomes(query, incomeCursor) else null
        currentCoroutineContext().ensureActive()
        if (expensePage != null) {
            loadedExpenses = (loadedExpenses + expensePage.items).distinctBy(Expense::id)
            expenseCursor = expensePage.nextCursor
            canLoadMoreExpenseResults = expensePage.canLoadMore
            hasLoadedExpensePage = true
        }
        if (incomePage != null) {
            loadedIncomes = (loadedIncomes + incomePage.items).distinctBy(Income::id)
            incomeCursor = incomePage.nextCursor
            canLoadMoreIncomeResults = incomePage.canLoadMore
            hasLoadedIncomePage = true
        }
        publishSnapshot()
    }

    private fun publishSnapshot() {
        val localization = localization ?: return
        val snapshot = buildTransactionSearchSnapshot(
            query = query,
            expenses = loadedExpenses,
            incomes = loadedIncomes,
            categories = latestCategories,
            groupingMode = groupingMode,
            localization = localization,
            canLoadMoreExpenseResults = canLoadMoreExpenseResults,
            canLoadMoreIncomeResults = canLoadMoreIncomeResults
        )
        snapshotState.value = snapshot
    }

}

private fun buildTransactionSearchSnapshot(
    query: String,
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    groupingMode: ExpenseGroupingMode,
    localization: IosGroupedLocalization,
    canLoadMoreExpenseResults: Boolean,
    canLoadMoreIncomeResults: Boolean
): IosTransactionSearchSnapshot {
    val categoriesById = categories.associateBy { it.id }
    val searchTokens = transactionSearchTokens(query)
    val preparedExpenses = expenses
        .asSequence()
        .filter { expense ->
            val categoryLabel = categoriesById[expense.categoryId]
                ?.let { localization.resolveCategoryName(it.id, it.name) }
                ?: localization.unknownCategory
            expense.matchesTransactionSearch(searchTokens, categoryLabel, localization.currencySymbol)
        }
        .map { expense ->
            with(localization) { prepareExpense(expense = expense, categoriesById = categoriesById) }
        }
        .toList()
    val preparedIncomes = incomes
        .asSequence()
        .filter { income ->
            val categoryLabel = income.categoryId
                ?.let(categoriesById::get)
                ?.let { localization.resolveCategoryName(it.id, it.name) }
                ?: localization.unknownCategory
            income.matchesTransactionSearch(searchTokens, categoryLabel, localization.currencySymbol)
        }
        .map { income ->
            with(localization) { prepareIncome(income = income, categoriesById = categoriesById) }
        }
        .toList()

    return IosTransactionSearchSnapshot(
        expenseSnapshot = IosGroupedExpensesSnapshot(
            totalAmountText = formatAmount(
                preparedExpenses.sumAmountOf(PreparedIosExpense::amount),
                localization.currencySymbol
            ),
            emptyStateText = localization.noSearchResults,
            sections = buildSections(
                groupedExpenses = groupPreparedExpensesByMode(preparedExpenses, groupingMode),
                groupingMode = groupingMode,
                screenType = IosGroupedExpensesScreenType.Monthly,
                localization = localization
            )
        ),
        incomeSnapshot = buildPreparedIncomesSnapshot(
            preparedIncomes = preparedIncomes,
            groupingMode = groupingMode,
            emptyStateText = localization.noSearchResults,
            localization = localization
        ),
        canLoadMoreExpenseResults = canLoadMoreExpenseResults,
        canLoadMoreIncomeResults = canLoadMoreIncomeResults
    )
}
