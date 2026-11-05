package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.di.iosKoin

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.monthBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class IosGroupedExpensesObserver internal constructor(
    initialGroupingMode: ExpenseGroupingMode,
    private val snapshotSource: (StateFlow<ExpenseGroupingMode>) -> Flow<IosGroupedExpensesSnapshot>
) {
    constructor(
        year: Int,
        month: Int,
        screenType: IosGroupedExpensesScreenType,
        categoryName: String?,
        dayOfMonth: Int? = null,
        initialGroupingMode: ExpenseGroupingMode
    ) : this(
        initialGroupingMode,
        { groupingMode -> groupedExpensesSnapshots(year, month, screenType, categoryName, dayOfMonth, groupingMode) }
    )

    private val groupingMode = MutableStateFlow(initialGroupingMode)

    val states: Flow<IosGroupedExpensesState> = iosReadStates(
        source = { snapshotSource(groupingMode) },
        data = IosGroupedExpensesState::Data,
        failed = IosGroupedExpensesState.Failed
    )

    fun setGroupingMode(groupingMode: ExpenseGroupingMode) {
        if (this.groupingMode.value != groupingMode) {
            this.groupingMode.value = groupingMode
        }
    }

    suspend fun deleteExpense(id: String) {
        repository().deleteExpense(id)
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        repository().deleteRecurringExpenseSeries(seriesId)
    }

    private fun repository(): ExpenseRepository =
        iosKoin().get()
}

private fun groupedExpensesSnapshots(
    year: Int,
    month: Int,
    screenType: IosGroupedExpensesScreenType,
    categoryName: String?,
    dayOfMonth: Int?,
    groupingMode: StateFlow<ExpenseGroupingMode>
): Flow<IosGroupedExpensesSnapshot> {
    val cacheKey = GroupedExpensesCacheKey(year, month, screenType, categoryName, dayOfMonth)
    return flow {
        val repository = iosKoin().get<ExpenseRepository>()
        repository.seedStarterCategoriesIfEmpty()
        val localization = loadIosGroupedLocalization()
        val (startMillis, endMillis) = monthBounds(year, month)
        emitAll(
            combine(
                repository.getExpensesBetween(startMillis, endMillis),
                repository.getAllCategories(),
                groupingMode
            ) { expenses, categories, currentGroupingMode ->
                val categoriesById = categories.associateBy { it.id }
                val preparedExpenses = expenses.map { expense ->
                    with(localization) { prepareExpense(expense = expense, categoriesById = categoriesById) }
                }
                buildSnapshotsCache(
                    preparedExpenses = preparedExpenses,
                    key = cacheKey,
                    localization = localization
                ).snapshotFor(currentGroupingMode)
            }
        )
    }
}

class IosRecurringExpensesObserver internal constructor(
    snapshotSource: () -> Flow<IosRecurringExpensesSnapshot>
) {
    constructor(year: Int, month: Int) : this({ recurringExpensesSnapshots(year, month) })

    val states: Flow<IosRecurringExpensesState> = iosReadStates(
        source = snapshotSource,
        data = IosRecurringExpensesState::Data,
        failed = IosRecurringExpensesState.Failed
    )
}

private fun recurringExpensesSnapshots(year: Int, month: Int): Flow<IosRecurringExpensesSnapshot> =
    flow {
        val repository = iosKoin().get<ExpenseRepository>()
        repository.seedStarterCategoriesIfEmpty()
        val localization = loadIosRecurringLocalization()
        emitAll(
            combine(
                repository.getRecurringExpenseOverviewForMonth(year = year, month = month),
                repository.getAllCategories()
            ) { overview, categories ->
                val categoriesById = categories.associateBy { it.id }
                val preparedExpenses = overview.expenses.map { expense ->
                    with(localization) { prepareRecurringExpense(expense = expense, categoriesById = categoriesById) }
                }
                buildRecurringExpensesSnapshot(
                    preparedExpenses = preparedExpenses,
                    localization = localization
                )
            }
        )
    }

class IosMonthlyIncomesObserver internal constructor(
    initialGroupingMode: ExpenseGroupingMode,
    private val snapshotSource: (StateFlow<ExpenseGroupingMode>) -> Flow<IosMonthlyIncomesSnapshot>
) {
    constructor(year: Int, month: Int, initialGroupingMode: ExpenseGroupingMode) : this(
        initialGroupingMode,
        { groupingMode -> monthlyIncomesSnapshots(year, month, groupingMode) }
    )

    private val groupingMode = MutableStateFlow(initialGroupingMode)

    val states: Flow<IosMonthlyIncomesState> = iosReadStates(
        source = { snapshotSource(groupingMode) },
        data = IosMonthlyIncomesState::Data,
        failed = IosMonthlyIncomesState.Failed
    )

    fun setGroupingMode(groupingMode: ExpenseGroupingMode) {
        if (this.groupingMode.value != groupingMode) {
            this.groupingMode.value = groupingMode
        }
    }

    suspend fun deleteIncome(id: String) {
        repository().deleteIncome(id)
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        repository().deleteRecurringIncomeSeries(seriesId)
    }

    private fun repository(): ExpenseRepository =
        iosKoin().get()
}

private fun monthlyIncomesSnapshots(
    year: Int,
    month: Int,
    groupingMode: StateFlow<ExpenseGroupingMode>
): Flow<IosMonthlyIncomesSnapshot> =
    flow {
        val repository = iosKoin().get<ExpenseRepository>()
        repository.seedStarterCategoriesIfEmpty()
        val localization = loadIosGroupedLocalization()
        val (startMillis, endMillis) = monthBounds(year, month)
        emitAll(
            combine(
                repository.getIncomesBetween(startMillis, endMillis),
                repository.getAllCategories(),
                groupingMode
            ) { incomes, categories, currentGroupingMode ->
                buildMonthlyIncomesSnapshot(
                    incomes = incomes,
                    categories = categories,
                    year = year,
                    month = month,
                    groupingMode = currentGroupingMode,
                    localization = localization
                )
            }
        )
    }
