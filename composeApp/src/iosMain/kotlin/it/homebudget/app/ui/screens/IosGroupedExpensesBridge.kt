package it.homebudget.app.ui.screens

import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatformTools
import kotlin.time.Clock
import kotlin.time.Instant

class IosGroupedExpenseRow(
    val id: String,
    val title: String,
    val subtitleText: String,
    val amountText: String
)

class IosGroupedExpenseSection(
    val id: String,
    val title: String,
    val totalAmountText: String,
    val rows: List<IosGroupedExpenseRow>
)

class IosGroupedExpensesSnapshot(
    val totalAmountText: String,
    val emptyStateText: String,
    val sections: List<IosGroupedExpenseSection>
)

internal data class GroupedExpensesCacheKey(
    val year: Int,
    val month: Int,
    val screenType: String,
    val categoryName: String?
)

private data class PreparedIosExpense(
    val id: String,
    val amount: BigInteger,
    val amountText: String,
    val categoryName: String,
    val description: String?,
    val dateText: String,
    val dateGroupTitleText: String,
    val dateMillis: Long,
    val year: Int,
    val month: Int,
    val isShared: Boolean
)

internal data class IosGroupedSnapshotsCache(
    val byCategory: IosGroupedExpensesSnapshot,
    val byDate: IosGroupedExpensesSnapshot
) {
    fun snapshotFor(groupingMode: String): IosGroupedExpensesSnapshot = when (groupingMode) {
        "date" -> byDate
        else -> byCategory
    }
}

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
            repository.insertDefaultCategoriesIfEmpty()
            combine(repository.getAllExpenses(), repository.getAllCategories()) { expenses, categories ->
                expenses to categories
            }.collect { (expenses, categories) ->
                state.value = buildStoreState(
                    expenses = expenses,
                    categories = categories,
                    trackedKeys = trackedKeys
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
                    key = key
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
        trackedKeys: Set<GroupedExpensesCacheKey>
    ): IosGroupedExpensesStoreState = withContext(Dispatchers.Default) {
        val categoriesById = categories.associateBy { it.id }
        val preparedExpenses = expenses.map { expense ->
            prepareExpense(
                expense = expense,
                categoriesById = categoriesById
            )
        }
        val cacheKeys = defaultPrewarmKeys(preparedExpenses) + trackedKeys
        val caches = cacheKeys.associateWith { key ->
            buildSnapshotsCache(
                preparedExpenses = preparedExpenses,
                key = key
            )
        }
        IosGroupedExpensesStoreState(
            isPrimed = true,
            preparedExpenses = preparedExpenses,
            caches = caches
        )
    }
}

class IosGroupedExpensesObserver(
    private val year: Int,
    private val month: Int,
    private val screenType: String,
    private val categoryName: String?,
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
        categoryName = categoryName
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
            val repository = KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
            repository.deleteExpense(id)
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

internal fun startIosGroupedExpensesStore() {
    ensureKoinStartedIfNeeded()
    KoinPlatformTools.defaultContext().get().get<IosGroupedExpensesStore>().start()
}

private fun buildSnapshotsCache(
    preparedExpenses: List<PreparedIosExpense>,
    key: GroupedExpensesCacheKey
): IosGroupedSnapshotsCache {
    val filteredExpenses = preparedExpenses.filter { expense ->
        expense.year == key.year &&
            expense.month == key.month &&
            includeExpense(expense, key.screenType) &&
            includeCategory(expense.categoryName, key.screenType, key.categoryName)
    }

    val totalAmountText = formatAmount(filteredExpenses.map { it.amount }.sumBigInteger())
    val emptyStateText = emptyStateText(key.screenType, key.categoryName)

    return IosGroupedSnapshotsCache(
        byCategory = IosGroupedExpensesSnapshot(
            totalAmountText = totalAmountText,
            emptyStateText = emptyStateText,
            sections = buildSections(
                groupedExpenses = filteredExpenses.groupBy { it.categoryName },
                groupingMode = "category",
                screenType = key.screenType
            )
        ),
        byDate = IosGroupedExpensesSnapshot(
            totalAmountText = totalAmountText,
            emptyStateText = emptyStateText,
            sections = buildSections(
                groupedExpenses = filteredExpenses.groupBy { it.dateGroupTitleText },
                groupingMode = "date",
                screenType = key.screenType
            )
        )
    )
}

private fun buildSections(
    groupedExpenses: Map<String, List<PreparedIosExpense>>,
    groupingMode: String,
    screenType: String
): List<IosGroupedExpenseSection> = groupedExpenses
    .toList()
    .sortedWith(
        when (groupingMode) {
            "date" -> compareByDescending<Pair<String, List<PreparedIosExpense>>> {
                it.second.maxOfOrNull(PreparedIosExpense::dateMillis) ?: Long.MIN_VALUE
            }
            else -> compareBy<Pair<String, List<PreparedIosExpense>>> { it.first }
        }
    )
    .map { (groupName, groupExpenses) ->
        val sortedExpenses = groupExpenses.sortedWith(
            compareByDescending<PreparedIosExpense> { it.dateMillis }
                .thenBy { it.categoryName }
                .thenBy { it.description.orEmpty() }
        )
        IosGroupedExpenseSection(
            id = groupName,
            title = groupName,
            totalAmountText = formatAmount(sortedExpenses.map { it.amount }.sumBigInteger()),
            rows = sortedExpenses.map { expense ->
                val expenseName = expense.description?.ifBlank { expenseFallbackTitle(screenType) }
                    ?: expenseFallbackTitle(screenType)
                IosGroupedExpenseRow(
                    id = expense.id,
                    title = if (groupingMode == "date") expense.categoryName else expenseName,
                    subtitleText = if (groupingMode == "date") expenseName else expense.dateText,
                    amountText = expense.amountText
                )
            }
        )
    }

private fun prepareExpense(
    expense: Expense,
    categoriesById: Map<String, Category>
): PreparedIosExpense {
    val localDate = expense.date.toLocalDate()
    return PreparedIosExpense(
        id = expense.id,
        amount = expense.amount,
        amountText = formatAmount(expense.amount),
        categoryName = categoriesById[expense.categoryId]?.name ?: "Unknown category",
        description = expense.description,
        dateText = formatDate(expense.date),
        dateGroupTitleText = formatDateGroupTitle(expense.date),
        dateMillis = expense.date,
        year = localDate.year,
        month = localDate.month.ordinal + 1,
        isShared = expense.isShared == 1L
    )
}

private fun defaultPrewarmKeys(preparedExpenses: List<PreparedIosExpense>): Set<GroupedExpensesCacheKey> {
    val (currentYear, currentMonth) = currentYearMonth()
    val keys = mutableSetOf(
        GroupedExpensesCacheKey(currentYear, currentMonth, "monthly", null),
        GroupedExpensesCacheKey(currentYear, currentMonth, "shared", null)
    )
    preparedExpenses
        .map { it.year to it.month }
        .distinct()
        .forEach { (year, month) ->
            keys += GroupedExpensesCacheKey(year, month, "monthly", null)
            keys += GroupedExpensesCacheKey(year, month, "shared", null)
        }
    return keys
}

private fun currentYearMonth(): Pair<Int, Int> {
    val now = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return now.year to (now.month.ordinal + 1)
}

private fun includeExpense(expense: PreparedIosExpense, screenType: String): Boolean = when (screenType) {
    "shared" -> expense.isShared
    else -> true
}

private fun includeCategory(groupName: String, screenType: String, categoryName: String?): Boolean = when (screenType) {
    "category" -> groupName == categoryName
    else -> true
}

private fun expenseFallbackTitle(screenType: String): String = when (screenType) {
    "shared" -> "Shared expense"
    else -> "Expense"
}

private fun emptyStateText(screenType: String, categoryName: String?): String = when (screenType) {
    "shared" -> "No shared expenses for this month"
    "category" -> "No expenses for ${categoryName ?: "this category"} this month"
    else -> "No expenses for this month"
}

private fun formatDate(epochMillis: Long): String {
    val date = epochMillis.toLocalDate()
    return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}

private fun formatDateGroupTitle(epochMillis: Long): String {
    val date = epochMillis.toLocalDate()
    return formatExpenseDateGroupTitle(date)
}

private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date

private fun ensureKoinStartedIfNeeded() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
