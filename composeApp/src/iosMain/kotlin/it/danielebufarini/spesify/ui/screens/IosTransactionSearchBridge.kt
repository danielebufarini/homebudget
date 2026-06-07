package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.TransactionPageCursor
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.data.sumAmountOf
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private var isLoadingPage = false
    private var hasStarted = false

    val snapshots: StateFlow<IosTransactionSearchSnapshot?> = snapshotState.asStateFlow()

    suspend fun start() {
        if (hasStarted) {
            return
        }

        hasStarted = true
        val repository = repository()
        repository.seedStarterCategoriesIfEmpty()
        localization = loadIosGroupedLocalization()
        latestCategories = repository.getAllCategories().first()
        loadNextPage(repository)
    }

    suspend fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode != groupingMode) {
            this.groupingMode = groupingMode
            publishSnapshot()
        }
    }

    suspend fun loadMoreResults() {
        loadNextPage(repository())
    }

    suspend fun deleteExpense(id: String) {
        repository().deleteExpense(id)
        loadedExpenses = loadedExpenses.filterNot { it.id == id }
        publishSnapshot()
    }

    suspend fun deleteIncome(id: String) {
        repository().deleteIncome(id)
        loadedIncomes = loadedIncomes.filterNot { it.id == id }
        publishSnapshot()
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        repository().deleteRecurringExpenseSeries(seriesId)
        loadedExpenses = loadedExpenses.filterNot { it.recurringSeriesId == seriesId }
        publishSnapshot()
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        repository().deleteRecurringIncomeSeries(seriesId)
        loadedIncomes = loadedIncomes.filterNot { it.recurringSeriesId == seriesId }
        publishSnapshot()
    }

    private suspend fun loadNextPage(repository: ExpenseRepository) {
        if (!hasStarted || isLoadingPage) {
            return
        }

        isLoadingPage = true
        try {
            val shouldLoadExpenses = !hasLoadedExpensePage || canLoadMoreExpenseResults
            val shouldLoadIncomes = !hasLoadedIncomePage || canLoadMoreIncomeResults

            if (!shouldLoadExpenses && !shouldLoadIncomes) {
                return
            }

            if (shouldLoadExpenses) {
                val nextExpensePage = repository.searchExpenseCandidatePage(
                    query = query,
                    limit = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE,
                    cursor = expenseCursor
                ).first()
                loadedExpenses = (loadedExpenses + nextExpensePage.items).distinctBy(Expense::id)
                expenseCursor = nextExpensePage.nextCursor
                canLoadMoreExpenseResults = nextExpensePage.canLoadMore
                hasLoadedExpensePage = true
            }

            if (shouldLoadIncomes) {
                val nextIncomePage = repository.searchIncomeCandidatePage(
                    query = query,
                    limit = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE,
                    cursor = incomeCursor
                ).first()
                loadedIncomes = (loadedIncomes + nextIncomePage.items).distinctBy(Income::id)
                incomeCursor = nextIncomePage.nextCursor
                canLoadMoreIncomeResults = nextIncomePage.canLoadMore
                hasLoadedIncomePage = true
            }

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
        snapshotState.value = snapshot
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
