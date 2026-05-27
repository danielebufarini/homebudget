package it.homebudget.app.ui.screens

import it.homebudget.app.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumAmountOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    private val scope = MainScope()
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private val loadedPageCount = MutableStateFlow(1)
    private var updatesJob: Job? = null
    private var onUpdate: ((IosTransactionSearchSnapshot) -> Unit)? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(onUpdate: (IosTransactionSearchSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        this.onUpdate = onUpdate
        updatesJob = scope.launch {
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            withContext(Dispatchers.Default) {
                repository.seedStarterCategoriesIfEmpty()
            }

            loadedPageCount.flatMapLatest { pageCount ->
                combine(
                    repository.searchExpenseCandidatePages(
                        query = query,
                        pageCount = pageCount,
                        pageSize = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
                    ),
                    repository.searchIncomeCandidatePages(
                        query = query,
                        pageCount = pageCount,
                        pageSize = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
                    ),
                    repository.getAllCategories(),
                    groupingMode
                ) { expenses, incomes, categories, groupingMode ->
                    TransactionSearchInput(
                        expenses = expenses,
                        incomes = incomes,
                        categories = categories,
                        groupingMode = groupingMode,
                        loadedPageCount = pageCount
                    )
                }
            }.flowOn(Dispatchers.Default).collect { input ->
                val localization = loadIosGroupedLocalization()
                val snapshot = withContext(Dispatchers.Default) {
                    buildTransactionSearchSnapshot(
                        query = query,
                        expenses = input.expenses,
                        incomes = input.incomes,
                        categories = input.categories,
                        groupingMode = input.groupingMode,
                        localization = localization,
                        loadedPageCount = input.loadedPageCount
                    )
                }
                onUpdate(snapshot)
            }
        }
    }

    fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode.value != groupingMode) {
            this.groupingMode.value = groupingMode
        }
    }

    fun loadMoreResults() {
        loadedPageCount.value += 1
    }

    fun deleteExpense(id: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>().deleteExpense(id)
            }
        }
    }

    fun deleteIncome(id: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>().deleteIncome(id)
            }
        }
    }

    fun deleteRecurringExpenseSeries(seriesId: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                    .deleteRecurringExpenseSeries(seriesId)
            }
        }
    }

    fun deleteRecurringIncomeSeries(seriesId: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                    .deleteRecurringIncomeSeries(seriesId)
            }
        }
    }

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
        onUpdate = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

private data class TransactionSearchInput(
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val categories: List<Category>,
    val groupingMode: String,
    val loadedPageCount: Int
)

private fun buildTransactionSearchSnapshot(
    query: String,
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    groupingMode: String,
    localization: IosGroupedLocalization,
    loadedPageCount: Int
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

    val loadedCandidateCount = loadedPageCount * DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE

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
        canLoadMoreExpenseResults = expenses.size >= loadedCandidateCount,
        canLoadMoreIncomeResults = incomes.size >= loadedCandidateCount
    )
}
