package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.data.sumAmountOf
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosTransactionSearchSnapshot(
    val expenseSnapshot: IosGroupedExpensesSnapshot,
    val incomeSnapshot: IosMonthlyIncomesSnapshot,
    val canLoadMoreExpenseResults: Boolean,
    val canLoadMoreIncomeResults: Boolean
)

class IosTransactionSearchObserver(
    private val query: String,
    initialGroupingMode: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var groupingMode = initialGroupingMode
    private var loadedPageCount = 0
    private var loadedExpenses: List<Expense> = emptyList()
    private var loadedIncomes: List<Income> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var localization: IosGroupedLocalization? = null
    private var canLoadMoreExpenseResults = false
    private var canLoadMoreIncomeResults = false
    private var isLoadingPage = false
    private var isObserving = false
    private var onUpdate: ((IosTransactionSearchSnapshot) -> Unit)? = null

    fun start(onUpdate: (IosTransactionSearchSnapshot) -> Unit) {
        if (isObserving) {
            return
        }

        this.onUpdate = onUpdate
        isObserving = true
        scope.launch {
            val repository = repository()
            repository.seedStarterCategoriesIfEmpty()
            localization = loadIosGroupedLocalization()
            latestCategories = repository.getAllCategories().first()
            loadNextPage(repository)
        }
    }

    fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode != groupingMode) {
            this.groupingMode = groupingMode
            scope.launch {
                publishSnapshot()
            }
        }
    }

    fun loadMoreResults() {
        scope.launch {
            loadNextPage(repository())
        }
    }

    fun deleteExpense(id: String) {
        scope.launch {
            repository().deleteExpense(id)
            loadedExpenses = loadedExpenses.filterNot { it.id == id }
            publishSnapshot()
        }
    }

    fun deleteIncome(id: String) {
        scope.launch {
            repository().deleteIncome(id)
            loadedIncomes = loadedIncomes.filterNot { it.id == id }
            publishSnapshot()
        }
    }

    fun deleteRecurringExpenseSeries(seriesId: String) {
        scope.launch {
            repository().deleteRecurringExpenseSeries(seriesId)
            loadedExpenses = loadedExpenses.filterNot { it.recurringSeriesId == seriesId }
            publishSnapshot()
        }
    }

    fun deleteRecurringIncomeSeries(seriesId: String) {
        scope.launch {
            repository().deleteRecurringIncomeSeries(seriesId)
            loadedIncomes = loadedIncomes.filterNot { it.recurringSeriesId == seriesId }
            publishSnapshot()
        }
    }

    fun stop() {
        isObserving = false
        onUpdate = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    private suspend fun loadNextPage(repository: ExpenseRepository) {
        if (!isObserving || isLoadingPage) {
            return
        }

        isLoadingPage = true
        try {
            val pageIndex = loadedPageCount
            val offset = pageIndex * DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
            val nextExpenses = repository.searchExpenseCandidates(
                query = query,
                limit = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE,
                offset = offset
            ).first()
            val nextIncomes = repository.searchIncomeCandidates(
                query = query,
                limit = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE,
                offset = offset
            ).first()

            loadedExpenses = (loadedExpenses + nextExpenses).distinctBy(Expense::id)
            loadedIncomes = (loadedIncomes + nextIncomes).distinctBy(Income::id)
            canLoadMoreExpenseResults = nextExpenses.size >= DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
            canLoadMoreIncomeResults = nextIncomes.size >= DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
            loadedPageCount += 1
            publishSnapshot()
        } finally {
            isLoadingPage = false
        }
    }

    private suspend fun publishSnapshot() {
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
        withContext(Dispatchers.Main) {
            onUpdate?.invoke(snapshot)
        }
    }

    private fun repository(): ExpenseRepository =
        KoinPlatformTools.defaultContext().get().get()
}

private fun buildTransactionSearchSnapshot(
    query: String,
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    groupingMode: String,
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
            prepareExpense(
                expense = expense,
                categoriesById = categoriesById,
                localization = localization
            )
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
            prepareIncome(
                income = income,
                categoriesById = categoriesById,
                localization = localization
            )
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
                screenType = "monthly",
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
