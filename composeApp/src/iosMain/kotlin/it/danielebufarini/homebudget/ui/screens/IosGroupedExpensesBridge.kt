package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.monthBounds
import it.danielebufarini.homebudget.util.IOSCancellable
import it.danielebufarini.homebudget.util.IOSFlowWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

class IosGroupedExpensesObserver(
    private val year: Int,
    private val month: Int,
    private val screenType: String,
    private val categoryName: String?,
    private val dayOfMonth: Int? = null,
    initialGroupingMode: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private var updatesSubscription: IOSCancellable? = null
    private var updatesWrapper: IOSFlowWrapper<IosGroupedExpensesSnapshot>? = null
    private var onUpdate: ((IosGroupedExpensesSnapshot) -> Unit)? = null
    private var isObserving = false
    private val cacheKey = GroupedExpensesCacheKey(
        year = year,
        month = month,
        screenType = screenType,
        categoryName = categoryName,
        dayOfMonth = dayOfMonth
    )

    fun start(onUpdate: (IosGroupedExpensesSnapshot) -> Unit) {
        if (isObserving) {
            return
        }

        isObserving = true
        this.onUpdate = onUpdate
        scope.launch {
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            repository.seedStarterCategoriesIfEmpty()
            val localization = loadIosGroupedLocalization()
            val (startMillis, endMillis) = monthBounds(year, month)
            val snapshotFlow = combine(
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

            val wrapper = IOSFlowWrapper(snapshotFlow)
            if (!isObserving) {
                wrapper.cancel()
                return@launch
            }
            updatesWrapper = wrapper
            updatesSubscription = wrapper.subscribe(
                onEach = { snapshot ->
                    this@IosGroupedExpensesObserver.onUpdate?.invoke(snapshot)
                }
            )
        }
    }

    fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode.value != groupingMode) {
            this.groupingMode.value = groupingMode
        }
    }

    fun deleteExpense(id: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteExpense(id)
        }
    }

    fun deleteRecurringExpenseSeries(seriesId: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteRecurringExpenseSeries(seriesId)
        }
    }

    fun stop() {
        isObserving = false
        updatesSubscription?.cancel()
        updatesSubscription = null
        updatesWrapper?.cancel()
        updatesWrapper = null
        onUpdate = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

class IosMonthlyIncomesObserver(
    private val year: Int,
    private val month: Int,
    initialGroupingMode: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private var updatesSubscription: IOSCancellable? = null
    private var updatesWrapper: IOSFlowWrapper<IosMonthlyIncomesSnapshot>? = null
    private var onUpdate: ((IosMonthlyIncomesSnapshot) -> Unit)? = null
    private var isObserving = false

    fun start(onUpdate: (IosMonthlyIncomesSnapshot) -> Unit) {
        if (isObserving) {
            return
        }

        isObserving = true
        this.onUpdate = onUpdate
        scope.launch {
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            repository.seedStarterCategoriesIfEmpty()
            val localization = loadIosGroupedLocalization()
            val (startMillis, endMillis) = monthBounds(year, month)
            val snapshotFlow = combine(
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

            val wrapper = IOSFlowWrapper(snapshotFlow)
            if (!isObserving) {
                wrapper.cancel()
                return@launch
            }
            updatesWrapper = wrapper
            updatesSubscription = wrapper.subscribe(
                onEach = { snapshot ->
                    this@IosMonthlyIncomesObserver.onUpdate?.invoke(snapshot)
                }
            )
        }
    }

    fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode.value != groupingMode) {
            this.groupingMode.value = groupingMode
        }
    }

    fun deleteIncome(id: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteIncome(id)
        }
    }

    fun deleteRecurringIncomeSeries(seriesId: String) {
        scope.launch {
            KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                .deleteRecurringIncomeSeries(seriesId)
        }
    }

    fun stop() {
        isObserving = false
        updatesSubscription?.cancel()
        updatesSubscription = null
        updatesWrapper?.cancel()
        updatesWrapper = null
        onUpdate = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
