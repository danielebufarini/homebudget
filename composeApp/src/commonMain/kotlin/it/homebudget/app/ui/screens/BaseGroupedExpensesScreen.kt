package it.homebudget.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Instant

private enum class ExpenseGroupingMode {
    ByCategory,
    ByDate
}

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int
) : Screen {

    protected abstract fun screenTitle(monthName: String): String
    protected abstract fun emptyStateText(): String
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true
    protected open fun canDeleteExpense(): Boolean = true
    protected open fun canAddExpense(): Boolean = false
    protected open fun monthNavigationDescriptor(): String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddExpense = { navigator?.push(AddExpenseScreen()) },
            onOpenExpense = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddExpense: () -> Unit,
        onOpenExpense: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val scope = rememberCoroutineScope()
        var selectedMonth by remember { mutableStateOf(MonthCursor(year, month)) }
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        var recurringExpenseToDelete by remember { mutableStateOf<Expense?>(null) }
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val filteredExpenses = remember(expenses, categories, selectedMonth) {
            expenses.filter { expense ->
                val localDate = expense.date.toLocalDate()
                val categoryName = categoriesById[expense.categoryId]?.name ?: "Unknown category"
                localDate.year == selectedMonth.year &&
                    localDate.month.ordinal + 1 == selectedMonth.month &&
                    includeExpense(expense) &&
                    includeCategory(categoryName)
            }
        }

        val groupedExpenses = remember(filteredExpenses, categories, groupingMode) {
            when (groupingMode) {
                ExpenseGroupingMode.ByCategory -> {
                    filteredExpenses
                        .groupBy { expense ->
                            categoriesById[expense.categoryId]?.name ?: "Unknown category"
                        }
                        .toList()
                        .sortedBy { it.first }
                        .map { (groupKey, groupExpenses) ->
                            val sortedExpenses = groupExpenses.sortedWith(
                                compareByDescending<Expense> { it.date }
                                    .thenBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                                    .thenBy { it.description ?: "" }
                            )
                            groupKey to sortedExpenses
                        }
                }
                ExpenseGroupingMode.ByDate -> {
                    filteredExpenses
                        .groupBy { expense -> expense.date.toLocalDate() }
                        .toList()
                        .sortedByDescending { (_, groupExpenses) ->
                            groupExpenses.maxOf { it.date }
                        }
                        .map { (groupDate, groupExpenses) ->
                            val sortedExpenses = groupExpenses.sortedWith(
                                compareByDescending<Expense> { it.date }
                                    .thenBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                                    .thenBy { it.description ?: "" }
                            )
                            formatDateGroupTitle(groupDate) to sortedExpenses
                        }
                }
            }
        }
        val totalAmount = remember(groupedExpenses) {
            groupedExpenses
                .flatMap { it.second }
                .map { it.amount }
                .sumBigInteger()
        }
        val deleteExpenseAction: ((String) -> Unit)? = if (canDeleteExpense()) {
            deleteAction@{ expenseId ->
                val expense = filteredExpenses.find { it.id == expenseId } ?: return@deleteAction
                if (expense.recurringSeriesId.isNullOrBlank()) {
                    scope.launch { repository.deleteExpense(expenseId) }
                } else {
                    recurringExpenseToDelete = expense
                }
                Unit
            }
        } else {
            null
        }
        val monthNavigationDescriptor = monthNavigationDescriptor()

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            if (monthNavigationDescriptor != null) {
                                MonthNavigationTitle(
                                    selectedMonth = selectedMonth,
                                    subtitle = "$monthNavigationDescriptor • ${formatAmount(totalAmount)}",
                                    onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                    onNextMonth = { selectedMonth = selectedMonth.next() }
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(screenTitle(monthName(selectedMonth.month)))
                                    Text(
                                        text = formatAmount(totalAmount),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text("Back")
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (!isIos && canAddExpense()) {
                        FloatingActionButton(
                            onClick = onAddExpense
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add expense"
                            )
                        }
                    }
                }
            ) { padding ->
                GroupedExpensesContent(
                    isIos = isIos,
                    groupedExpenses = groupedExpenses,
                    categoriesById = categoriesById,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    groupingMode = groupingMode,
                    onGroupingModeChange = { groupingMode = it },
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = deleteExpenseAction
                )
            }
        } else {
            GroupedExpensesContent(
                isIos = isIos,
                groupedExpenses = groupedExpenses,
                categoriesById = categoriesById,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                groupingMode = groupingMode,
                onGroupingModeChange = { groupingMode = it },
                onOpenExpense = onOpenExpense,
                onDeleteExpense = deleteExpenseAction
            )
        }

        recurringExpenseToDelete?.let { expense ->
            RecurringSeriesActionDialog(
                title = "Delete recurring expense?",
                message = "Do you want to delete only this expense or the whole recurring series?",
                onThisInstanceOnly = {
                    recurringExpenseToDelete = null
                    scope.launch {
                        repository.deleteExpense(expense.id)
                    }
                },
                onWholeSeries = {
                    recurringExpenseToDelete = null
                    scope.launch {
                        repository.deleteRecurringExpenseSeries(expense.recurringSeriesId.orEmpty())
                    }
                },
                onDismiss = {
                    recurringExpenseToDelete = null
                }
            )
        }
    }

    @Composable
    private fun GroupedExpensesContent(
        isIos: Boolean,
        groupedExpenses: List<Pair<String, List<Expense>>>,
        categoriesById: Map<String, Category>,
        modifier: Modifier,
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        onOpenExpense: (String) -> Unit,
        onDeleteExpense: ((String) -> Unit)?
    ) {
        Box(modifier = modifier) {
            val listModifier = Modifier
                .fillMaxSize()
                .padding(bottom = 84.dp)

            if (!isIos) {
                AndroidGroupedExpensesRecyclerView(
                    groupedExpenses = groupedExpenses,
                    categoriesById = categoriesById,
                    isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                    modifier = listModifier,
                    emptyStateText = emptyStateText(),
                    expenseFallbackTitle = expenseFallbackTitle(),
                    groupsExpandedByDefault = groupsExpandedByDefault(),
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = onDeleteExpense
                )
            } else {
                GroupedExpensesList(
                    groupedExpenses = groupedExpenses,
                    categoriesById = categoriesById,
                    groupingMode = groupingMode,
                    modifier = listModifier,
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = onDeleteExpense
                )
            }

            GroupingModeButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GroupedExpensesList(
        groupedExpenses: List<Pair<String, List<Expense>>>,
        categoriesById: Map<String, Category>,
        groupingMode: ExpenseGroupingMode,
        modifier: Modifier,
        onOpenExpense: (String) -> Unit,
        onDeleteExpense: ((String) -> Unit)?
    ) {
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }

        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groupedExpenses.isEmpty()) {
                item {
                    PlatformCard {
                        Text(
                            text = emptyStateText(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                return@LazyColumn
            }

            groupedExpenses.forEach { (categoryName, categoryExpenses) ->
                item(key = categoryName) {
                    val expanded = expandedState[categoryName] ?: groupsExpandedByDefault()
                    PlatformCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedState[categoryName] = !expanded
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = categoryName,
                                    modifier = Modifier.fillMaxWidth(0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (expanded) {
                                HorizontalDivider()
                                categoryExpenses.forEach { expense ->
                                    key(expense.id) {
                                        val expenseName = expense.description?.ifBlank { expenseFallbackTitle() }
                                            ?: expenseFallbackTitle()
                                        val categoryName = categoriesById[expense.categoryId]?.name ?: "Unknown category"
                                        val rowTitle = if (groupingMode == ExpenseGroupingMode.ByDate) {
                                            categoryName
                                        } else {
                                            expenseName
                                        }
                                        val rowSubtitleText = if (groupingMode == ExpenseGroupingMode.ByDate) {
                                            expenseName
                                        } else {
                                            formatDate(expense.date)
                                        }
                                        val rowAmountText = formatAmount(expense.amount)

                                        if (onDeleteExpense == null) {
                                            ExpenseListItemRow(
                                                title = rowTitle,
                                                subtitleText = rowSubtitleText,
                                                amountText = rowAmountText,
                                                onClick = {
                                                    onOpenExpense(expense.id)
                                                }
                                            )
                                        } else {
                                            val scope = rememberCoroutineScope()
                                            val currentOnDeleteExpense by rememberUpdatedState(onDeleteExpense)
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                positionalThreshold = { distance ->
                                                    distance * 0.35f
                                                }
                                            )
                                            val handleDismiss = remember(expense.id, dismissState, scope) {
                                                { dismissValue: SwipeToDismissBoxValue ->
                                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                        currentOnDeleteExpense(expense.id)
                                                        scope.launch {
                                                            dismissState.reset()
                                                        }
                                                    }
                                                }
                                            }

                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = false,
                                                onDismiss = handleDismiss,
                                                backgroundContent = {
                                                    DeleteExpenseBackground()
                                                }
                                            ) {
                                                ExpenseListItemRow(
                                                    title = rowTitle,
                                                    subtitleText = rowSubtitleText,
                                                    amountText = rowAmountText,
                                                    onClick = {
                                                        onOpenExpense(expense.id)
                                                    }
                                                )
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected fun formatDate(epochMillis: Long): String {
        val date = epochMillis.toLocalDate()
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }

    protected fun formatDateGroupTitle(date: kotlinx.datetime.LocalDate): String {
        return formatExpenseDateGroupTitle(date)
    }

    @Composable
    private fun GroupingModeButtons(
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        modifier: Modifier = Modifier
    ) {
        if (!rememberIsIosPlatform()) {
            AndroidGroupingModeSegmentedButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                modifier = modifier
            )
            return
        }

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupingModeButton(
                label = "By Category",
                selected = groupingMode == ExpenseGroupingMode.ByCategory,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByCategory) }
            )
            GroupingModeButton(
                label = "By Date",
                selected = groupingMode == ExpenseGroupingMode.ByDate,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByDate) }
            )
        }
    }

    @Composable
    private fun AndroidGroupingModeSegmentedButtons(
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val options = listOf(
            ExpenseGroupingMode.ByCategory to "By Category",
            ExpenseGroupingMode.ByDate to "By Date"
        )

        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = groupingMode == mode,
                    onClick = { onGroupingModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    ),
                    icon = {}
                ) {
                    Text(label)
                }
            }
        }
    }

    @Composable
    private fun GroupingModeButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        if (selected) {
            FilledTonalButton(
                onClick = onClick,
                colors = homeBudgetFilledTonalButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                colors = homeBudgetOutlinedButtonColors(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label)
            }
        }
    }

    val months = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )

    protected fun monthName(month: Int): String {

        return months[month - 1]
    }

    private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
