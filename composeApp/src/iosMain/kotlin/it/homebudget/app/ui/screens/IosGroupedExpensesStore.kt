package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

private data class IosGroupedExpensesStoreState(
    val isPrimed: Boolean = false,
    val preparedExpenses: List<PreparedIosExpense> = emptyList(),
    val caches: Map<GroupedExpensesCacheKey, IosGroupedSnapshotsCache> = emptyMap()
)

internal class IosGroupedExpensesStore(
    private val repository: ExpenseRepository
) {
    private val scope = MainScope()
    private val state = MutableStateFlow(IosGroupedExpensesStoreState())
    private var updatesJob: Job? = null
    private var trackedKeys: Set<GroupedExpensesCacheKey> = emptySet()

    fun start() {
        if (updatesJob != null) {
            return
        }

        updatesJob = scope.launch {
            withContext(Dispatchers.Default) {
                repository.seedStarterCategoriesIfEmpty()
            }
            combine(repository.getAllExpenses(), repository.getAllCategories()) { expenses, categories ->
                expenses to categories
            }.flowOn(Dispatchers.Default).collect { (expenses, categories) ->
                val localization = loadIosGroupedLocalization()
                state.value = buildStoreState(
                    expenses = expenses,
                    categories = categories,
                    trackedKeys = trackedKeys,
                    localization = localization
                )
            }
        }
    }

    fun trackKey(key: GroupedExpensesCacheKey) {
        if (key in trackedKeys) {
            return
        }

        trackedKeys = trackedKeys + key
        val currentState = state.value
        if (!currentState.isPrimed || key in currentState.caches) {
            return
        }

        scope.launch {
            val cache = withContext(Dispatchers.Default) {
                buildSnapshotsCache(
                    preparedExpenses = currentState.preparedExpenses,
                    key = key,
                    localization = loadIosGroupedLocalization()
                )
            }
            val latestState = state.value
            if (key !in latestState.caches) {
                state.value = latestState.copy(
                    caches = latestState.caches + (key to cache)
                )
            }
        }
    }

    fun currentCache(key: GroupedExpensesCacheKey): IosGroupedSnapshotsCache? {
        val currentState = state.value
        return currentState.caches[key].takeIf { currentState.isPrimed }
    }

    fun observeCache(key: GroupedExpensesCacheKey): Flow<IosGroupedSnapshotsCache> = state
        .map { snapshotState ->
            if (snapshotState.isPrimed) {
                snapshotState.caches[key]
            } else {
                null
            }
        }
        .filterNotNull()
        .distinctUntilChanged()

    private suspend fun buildStoreState(
        expenses: List<Expense>,
        categories: List<Category>,
        trackedKeys: Set<GroupedExpensesCacheKey>,
        localization: IosGroupedLocalization
    ): IosGroupedExpensesStoreState = withContext(Dispatchers.Default) {
        val categoriesById = categories.associateBy { it.id }
        val preparedExpenses = expenses.map { expense ->
            prepareExpense(
                expense = expense,
                categoriesById = categoriesById,
                localization = localization
            )
        }
        val cacheKeys = defaultPrewarmKeys(preparedExpenses) + trackedKeys
        val caches = cacheKeys.associateWith { key ->
            buildSnapshotsCache(
                preparedExpenses = preparedExpenses,
                key = key,
                localization = localization
            )
        }
        IosGroupedExpensesStoreState(
            isPrimed = true,
            preparedExpenses = preparedExpenses,
            caches = caches
        )
    }
}

internal fun startIosGroupedExpensesStore() {
    ensureKoinStartedIfNeeded()
    KoinPlatformTools.defaultContext().get().get<IosGroupedExpensesStore>().start()
}

private fun ensureKoinStartedIfNeeded() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
