package it.homebudget.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Instant

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int
) : Screen {

    protected abstract fun screenTitle(monthName: String): String
    protected abstract fun emptyStateText(): String
    protected abstract fun categoryCountLabel(count: Int): String
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true
    protected open fun canDeleteExpense(): Boolean = false
    protected open fun useAndroidExpandableList(): Boolean = false

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
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
        onOpenExpense: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val scope = rememberCoroutineScope()
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val filteredExpenses = remember(expenses, year, month) {
            expenses.filter { expense ->
                val localDate = expense.date.toLocalDate()
                localDate.year == year &&
                    localDate.month.ordinal + 1 == month &&
                    includeExpense(expense)
            }
        }

        val groupedExpenses = remember(filteredExpenses, categories) {
            filteredExpenses
                .groupBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                .filterKeys { includeCategory(it) }
                .toList()
                .sortedBy { it.first }
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
                }
            ) { padding ->
                if (!isIos && useAndroidExpandableList()) {
                    AndroidExpandableGroupedExpensesList(
                        groupedExpenses = groupedExpenses,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        onOpenExpense = onOpenExpense,
                        onDeleteExpense = deleteExpenseAction
                    )
                } else {
                    GroupedExpensesList(
                        groupedExpenses = groupedExpenses,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        onOpenExpense = onOpenExpense,
                        onDeleteExpense = deleteExpenseAction
                    )
                }
            }
        } else {
            if (!isIos && useAndroidExpandableList()) {
                AndroidExpandableGroupedExpensesList(
                    groupedExpenses = groupedExpenses,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = deleteExpenseAction
                )
            } else {
                GroupedExpensesList(
                    groupedExpenses = groupedExpenses,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = deleteExpenseAction
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GroupedExpensesList(
        groupedExpenses: List<Pair<String, List<Expense>>>,
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
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.72f)
                                ) {
                                    Text(categoryName)
                                    Text(
                                        text = categoryCountLabel(categoryExpenses.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (expanded) {
                                HorizontalDivider()
                                categoryExpenses
                                    .sortedByDescending { it.date }
                                    .forEach { expense ->
                                        val rowTitle = expense.description?.ifBlank { expenseFallbackTitle() }
                                            ?: expenseFallbackTitle()
                                        val rowDateText = formatDate(expense.date)
                                        val rowAmountText = formatAmount(expense.amount)

                                        if (onDeleteExpense == null) {
                                            ExpenseListItemRow(
                                                title = rowTitle,
                                                dateText = rowDateText,
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
                                                    dateText = rowDateText,
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

    @Composable
    private fun AndroidExpandableGroupedExpensesList(
        groupedExpenses: List<Pair<String, List<Expense>>>,
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
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "SectionChevronRotation"
                    )

                    PlatformCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedState[categoryName] = !expanded
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(categoryName)
                                    Text(
                                        text = categoryCountLabel(categoryExpenses.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Collapse section" else "Expand section",
                                    modifier = Modifier.rotate(rotationAngle)
                                )
                            }

                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    HorizontalDivider()
                                    categoryExpenses
                                        .sortedByDescending { it.date }
                                        .forEach { expense ->
                                            key(expense.id) {
                                                ExpenseRow(
                                                    expense = expense,
                                                    expenseFallbackTitle = expenseFallbackTitle(),
                                                    onOpenExpense = onOpenExpense,
                                                    onDeleteExpense = onDeleteExpense
                                                )
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ExpenseRow(
        expense: Expense,
        expenseFallbackTitle: String,
        onOpenExpense: (String) -> Unit,
        onDeleteExpense: ((String) -> Unit)?
    ) {
        val rowTitle = expense.description?.ifBlank { expenseFallbackTitle } ?: expenseFallbackTitle
        val rowDateText = formatDate(expense.date)
        val rowAmountText = formatAmount(expense.amount)

        if (onDeleteExpense == null) {
            ExpenseListItemRow(
                title = rowTitle,
                dateText = rowDateText,
                amountText = rowAmountText,
                onClick = {
                    onOpenExpense(expense.id)
                }
            )
            return
        }

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
                dateText = rowDateText,
                amountText = rowAmountText,
                onClick = {
                    onOpenExpense(expense.id)
                }
            )
        }
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

    protected fun formatDate(epochMillis: Long): String {
        val date = epochMillis.toLocalDate()
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
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
