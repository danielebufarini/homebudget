package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.monthBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.koin.mp.KoinPlatformTools

class IosGroupedExpensesObserver(
    private val year: Int,
    private val month: Int,
    private val screenType: String,
    private val categoryName: String?,
    private val dayOfMonth: Int? = null,
    initialGroupingMode: String
) {
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private val cacheKey = GroupedExpensesCacheKey(
        year = year,
        month = month,
        screenType = screenType,
        categoryName = categoryName,
        dayOfMonth = dayOfMonth
    )

    val snapshots = flow {
        val repository = repository()
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
                    prepareExpense(
                        expense = expense,
                        categoriesById = categoriesById,
                        localization = localization
                    )
                }
                buildSnapshotsCache(
                    preparedExpenses = preparedExpenses,
                    key = cacheKey,
                    localization = localization
                ).snapshotFor(currentGroupingMode)
            }
        )
    }.flowOn(Dispatchers.Default)

    fun setGroupingMode(groupingMode: String) {
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
        KoinPlatformTools.defaultContext().get().get()
}

class IosMonthlyIncomesObserver(
    private val year: Int,
    private val month: Int,
    initialGroupingMode: String
) {
    private val groupingMode = MutableStateFlow(initialGroupingMode)

    val snapshots = flow {
        val repository = repository()
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
    }.flowOn(Dispatchers.Default)

    fun setGroupingMode(groupingMode: String) {
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
        KoinPlatformTools.defaultContext().get().get()
}
