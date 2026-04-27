package it.homebudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
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
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val filteredExpenses = remember(expenses, categories, year, month) {
            expenses.filter { expense ->
                val localDate = expense.date.toLocalDate()
                val categoryName = categoriesById[expense.categoryId]?.name ?: "Unknown category"
                localDate.year == year &&
                    localDate.month.ordinal + 1 == month &&
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
            { expenseId: String ->
                scope.launch {
                    repository.deleteExpense(expenseId)
                }
            }
        } else {
            null
        }

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(screenTitle(monthName(month)))
                                Text(
                                    text = formatAmount(totalAmount),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                    if (canAddExpense()) {
                        FloatingActionButton(onClick = onAddExpense) {
                            Text("+")
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
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { value ->
                                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                                        onDeleteExpense(expense.id)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                },
                                                positionalThreshold = { distance ->
                                                    distance * 0.35f
                                                }
                                            )

                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = false,
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

    protected fun formatDate(epochMillis: Long): String {
        val date = epochMillis.toLocalDate()
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }

    protected fun formatDateGroupTitle(epochMillis: Long): String {
        return formatDateGroupTitle(epochMillis.toLocalDate())
    }

    protected fun formatDateGroupTitle(date: kotlinx.datetime.LocalDate): String {
        return "${date.day.toString().padStart(2, '0')} ${monthShortName(date.month.ordinal)} ${date.year}"
    }

    private fun monthShortName(monthOrdinal: Int): String {
        val names = listOf(
            "Jan",
            "Feb",
            "Mar",
            "Apr",
            "May",
            "Jun",
            "Jul",
            "Aug",
            "Sep",
            "Oct",
            "Nov",
            "Dec"
        )
        return names.getOrElse(monthOrdinal) { "" }
    }

    @Composable
    private fun DeleteExpenseBackground() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete expense",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    }

    @Composable
    private fun GroupingModeButtons(
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        modifier: Modifier = Modifier
    ) {
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
    private fun GroupingModeButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        if (selected) {
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label)
            }
        }
    }

    protected fun monthName(month: Int): String {
        return listOf(
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
        )[month - 1]
    }

    private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
