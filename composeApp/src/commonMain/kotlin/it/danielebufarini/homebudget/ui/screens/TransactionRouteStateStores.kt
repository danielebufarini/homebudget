package it.danielebufarini.homebudget.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind

@Stable
internal class GroupedTransactionRouteState private constructor(
    selectedMonth: MonthCursor,
    groupingMode: ExpenseGroupingMode,
    localSearchPageCount: Int,
) {
    constructor(initialMonth: MonthCursor) : this(
        selectedMonth = initialMonth,
        groupingMode = ExpenseGroupingMode.ByCategory,
        localSearchPageCount = 1,
    )

    var selectedMonth by mutableStateOf(selectedMonth)
        private set

    var groupingMode by mutableStateOf(groupingMode)
        private set

    var localSearchPageCount by mutableIntStateOf(localSearchPageCount.coerceAtLeast(1))
        private set

    fun selectGroupingMode(mode: ExpenseGroupingMode) {
        groupingMode = mode
    }

    fun previousMonth() {
        selectedMonth = selectedMonth.previous()
    }

    fun nextMonth() {
        selectedMonth = selectedMonth.next()
    }

    fun loadNextSearchPage() {
        localSearchPageCount += 1
    }

    fun updateLocalSearchPageCount(count: Int) {
        localSearchPageCount = count.coerceAtLeast(1)
    }

    companion object {
        val Saver: Saver<GroupedTransactionRouteState, List<Any>> = Saver(
            save = { state ->
                listOf(
                    state.selectedMonth.year,
                    state.selectedMonth.month,
                    state.groupingMode.name,
                    state.localSearchPageCount,
                )
            },
            restore = { restored ->
                GroupedTransactionRouteState(
                    selectedMonth = MonthCursor(
                        year = restored.getOrNull(0) as? Int ?: 1970,
                        month = restored.getOrNull(1) as? Int ?: 1,
                    ),
                    groupingMode = expenseGroupingModeOrDefault(restored.getOrNull(2) as? String),
                    localSearchPageCount = restored.getOrNull(3) as? Int ?: 1,
                )
            },
        )
    }
}

@Composable
internal fun rememberGroupedTransactionRouteState(
    initialMonth: MonthCursor,
    searchQuery: String,
): GroupedTransactionRouteState =
    rememberSaveable(
        initialMonth.year,
        initialMonth.month,
        searchQuery,
        saver = GroupedTransactionRouteState.Saver,
    ) {
        GroupedTransactionRouteState(initialMonth)
    }

@Stable
internal class MonthlyTransactionsRouteState private constructor(
    selectedMonth: MonthCursor,
    selectedKind: TransactionEditorKind,
    searchPageCount: Int,
) {
    constructor(
        initialMonth: MonthCursor,
        initialKind: TransactionEditorKind,
    ) : this(
        selectedMonth = initialMonth,
        selectedKind = initialKind,
        searchPageCount = 1,
    )

    var selectedMonth by mutableStateOf(selectedMonth)
        private set

    var selectedKind by mutableStateOf(selectedKind)
        private set

    var searchPageCount by mutableIntStateOf(searchPageCount.coerceAtLeast(1))
        private set

    fun selectKind(kind: TransactionEditorKind) {
        if (kind == selectedKind) return

        selectedKind = kind
        searchPageCount = 1
    }

    fun previousMonth() {
        selectedMonth = selectedMonth.previous()
    }

    fun nextMonth() {
        selectedMonth = selectedMonth.next()
    }

    fun loadNextSearchPage() {
        searchPageCount += 1
    }

    companion object {
        val Saver: Saver<MonthlyTransactionsRouteState, List<Any>> = Saver(
            save = { state ->
                listOf(
                    state.selectedMonth.year,
                    state.selectedMonth.month,
                    state.selectedKind.name,
                    state.searchPageCount,
                )
            },
            restore = { restored ->
                MonthlyTransactionsRouteState(
                    selectedMonth = MonthCursor(
                        year = restored.getOrNull(0) as? Int ?: 1970,
                        month = restored.getOrNull(1) as? Int ?: 1,
                    ),
                    selectedKind = transactionEditorKindOrDefault(restored.getOrNull(2) as? String),
                    searchPageCount = restored.getOrNull(3) as? Int ?: 1,
                )
            },
        )
    }
}

@Composable
internal fun rememberMonthlyTransactionsRouteState(
    initialMonth: MonthCursor,
    initialKind: TransactionEditorKind,
    searchQuery: String,
): MonthlyTransactionsRouteState =
    rememberSaveable(
        initialMonth.year,
        initialMonth.month,
        initialKind.name,
        searchQuery,
        saver = MonthlyTransactionsRouteState.Saver,
    ) {
        MonthlyTransactionsRouteState(
            initialMonth = initialMonth,
            initialKind = initialKind,
        )
    }

@Stable
internal class AddTransactionRouteState(
    initialKind: TransactionEditorKind,
) {
    var selectedKind by mutableStateOf(initialKind)
        private set

    fun selectKind(kind: TransactionEditorKind) {
        selectedKind = kind
    }

    companion object {
        val Saver: Saver<AddTransactionRouteState, String> = Saver(
            save = { state -> state.selectedKind.name },
            restore = { restored -> AddTransactionRouteState(transactionEditorKindOrDefault(restored)) },
        )
    }
}

@Composable
internal fun rememberAddTransactionRouteState(
    initialKind: TransactionEditorKind,
): AddTransactionRouteState =
    rememberSaveable(
        initialKind.name,
        saver = AddTransactionRouteState.Saver,
    ) {
        AddTransactionRouteState(initialKind)
    }

private fun expenseGroupingModeOrDefault(name: String?): ExpenseGroupingMode =
    runCatching {
        if (name == null) ExpenseGroupingMode.ByCategory else ExpenseGroupingMode.valueOf(name)
    }.getOrDefault(ExpenseGroupingMode.ByCategory)

private fun transactionEditorKindOrDefault(name: String?): TransactionEditorKind =
    runCatching {
        if (name == null) TransactionEditorKind.Expense else TransactionEditorKind.valueOf(name)
    }.getOrDefault(TransactionEditorKind.Expense)
