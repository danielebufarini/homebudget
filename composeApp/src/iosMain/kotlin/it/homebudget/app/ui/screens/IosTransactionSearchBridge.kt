package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumAmountOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosTransactionSearchSnapshot(
    val expenseSnapshot: IosGroupedExpensesSnapshot,
    val incomeSnapshot: IosMonthlyIncomesSnapshot
)

class IosTransactionSearchObserver(
    private val query: String,
    initialGroupingMode: String
) {
    private val scope = MainScope()
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private var updatesJob: Job? = null
    private var onUpdate: ((IosTransactionSearchSnapshot) -> Unit)? = null

    fun start(onUpdate: (IosTransactionSearchSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        this.onUpdate = onUpdate
        updatesJob = scope.launch {
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            repository.seedStarterCategoriesIfEmpty()

            combine(
                repository.getAllExpenses(),
                repository.getAllIncomes(),
                repository.getAllCategories(),
                groupingMode,
                ::TransactionSearchInput
            ).collect { input ->
                val localization = loadIosGroupedLocalization()
                val snapshot = withContext(Dispatchers.Default) {
                    buildTransactionSearchSnapshot(
                        query = query,
                        expenses = input.expenses,
                        incomes = input.incomes,
                        categories = input.categories,
                        groupingMode = input.groupingMode,
                        localization = localization
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

    fun deleteExpense(id: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>().deleteExpense(id)
        }
    }

    fun deleteIncome(id: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>().deleteIncome(id)
        }
    }

    fun deleteRecurringExpenseSeries(seriesId: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteRecurringExpenseSeries(seriesId)
        }
    }

    fun deleteRecurringIncomeSeries(seriesId: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteRecurringIncomeSeries(seriesId)
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
    val groupingMode: String
)

private fun buildTransactionSearchSnapshot(
    query: String,
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    groupingMode: String,
    localization: IosGroupedLocalization
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
        )
    )
}
