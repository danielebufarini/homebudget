package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.monthBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosGroupedExpensesObserver(
    private val year: Int,
    private val month: Int,
    private val screenType: String,
    private val categoryName: String?,
    private val dayOfMonth: Int? = null,
    initialGroupingMode: String
) {
    private val scope = MainScope()
    private var updatesJob: Job? = null
    private var onUpdate: ((IosGroupedExpensesSnapshot) -> Unit)? = null
    private var latestSnapshotsCache: IosGroupedSnapshotsCache? = null
    private var groupingMode: String = initialGroupingMode
    private val cacheKey = GroupedExpensesCacheKey(
        year = year,
        month = month,
        screenType = screenType,
        categoryName = categoryName,
        dayOfMonth = dayOfMonth
    )

    private val store: IosGroupedExpensesStore by lazy {
        startIosGroupedExpensesStore()
        KoinPlatformTools.defaultContext().get().get<IosGroupedExpensesStore>()
    }

    fun start(onUpdate: (IosGroupedExpensesSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        this.onUpdate = onUpdate
        store.currentCache(cacheKey)?.let { cache ->
            latestSnapshotsCache = cache
            onUpdate(cache.snapshotFor(groupingMode))
        }
        updatesJob = scope.launch {
            store.trackKey(cacheKey)
            store.observeCache(cacheKey).collect { cache ->
                latestSnapshotsCache = cache
                emitSnapshot()
            }
        }
    }

    fun setGroupingMode(groupingMode: String) {
        if (this.groupingMode == groupingMode) {
            return
        }

        this.groupingMode = groupingMode
        if (updatesJob != null) {
            scope.launch {
                emitSnapshot()
            }
        }
    }

    fun deleteExpense(id: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                    .deleteExpense(id)
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

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
        onUpdate = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    private suspend fun emitSnapshot() {
        val callback = onUpdate ?: return
        val snapshot = latestSnapshotsCache?.snapshotFor(groupingMode) ?: return
        callback(snapshot)
    }
}

class IosMonthlyIncomesObserver(
    private val year: Int,
    private val month: Int,
    initialGroupingMode: String
) {
    private val scope = MainScope()
    private val groupingMode = MutableStateFlow(initialGroupingMode)
    private var updatesJob: Job? = null
    private var onUpdate: ((IosMonthlyIncomesSnapshot) -> Unit)? = null

    fun start(onUpdate: (IosMonthlyIncomesSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        this.onUpdate = onUpdate
        updatesJob = scope.launch {
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            withContext(Dispatchers.Default) {
                repository.seedStarterCategoriesIfEmpty()
            }
            val (startMillis, endMillis) = monthBounds(year, month)

            combine(
                repository.getIncomesBetween(startMillis, endMillis),
                repository.getAllCategories(),
                groupingMode
            ) { incomes, categories, currentGroupingMode ->
                Triple(incomes, categories, currentGroupingMode)
            }.flowOn(Dispatchers.Default).collect { (incomes, categories, currentGroupingMode) ->
                val localization = loadIosGroupedLocalization()
                val snapshot = withContext(Dispatchers.Default) {
                    buildMonthlyIncomesSnapshot(
                        incomes = incomes,
                        categories = categories,
                        year = year,
                        month = month,
                        groupingMode = currentGroupingMode,
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

    fun deleteIncome(id: String) {
        scope.launch {
            withContext(Dispatchers.Default) {
                KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
                    .deleteIncome(id)
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
