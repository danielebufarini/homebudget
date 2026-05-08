package it.homebudget.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.by_category
import homebudget.composeapp.generated.resources.by_date
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.full_month_names
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unknown_category
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.monthBounds
import it.homebudget.app.data.sumBigIntegerOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import it.homebudget.app.localization.formatResourceArgs
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private enum class ExpenseGroupingMode {
    ByCategory,
    ByDate
}

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int
) : Screen {

    @Composable
    protected abstract fun screenTitle(monthName: String): String
    @Composable
    protected abstract fun emptyStateText(): String
    @Composable
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true
    protected open fun canDeleteExpense(): Boolean = true
    protected open fun canAddExpense(): Boolean = false
    @Composable
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
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val backLabel = stringResource(Res.string.back)
        val byCategoryLabel = stringResource(Res.string.by_category)
        val byDateLabel = stringResource(Res.string.by_date)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val deleteLabel = stringResource(Res.string.delete)
        val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
        val recurringExpenseDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message)
        val unknownCategoryLabel = stringResource(Res.string.unknown_category)
        val fullMonthNames = stringArrayResource(Res.array.full_month_names)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val resolveCategoryName = rememberCategoryNameResolver()
        val emptyStateText = emptyStateText()
        val expenseFallbackTitle = expenseFallbackTitle()
        val navigationDescriptor = monthNavigationDescriptor()
        var selectedMonth by remember { mutableStateOf(MonthCursor(year, month)) }
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
        var recurringExpenseToDelete by remember { mutableStateOf<Expense?>(null) }
        val (monthStartMillis, monthEndMillis) = remember(selectedMonth) {
            monthBounds(selectedMonth.year, selectedMonth.month)
        }
        val expensesFlow = remember(repository, monthStartMillis, monthEndMillis) {
            repository.getExpensesBetween(monthStartMillis, monthEndMillis)
        }
        val expenses by expensesFlow.collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        EnsureDefaultCategoriesInserted(repository)

        val filteredExpenses = remember(
            expenses,
            categoriesById,
            selectedMonth,
            resolveCategoryName,
            unknownCategoryLabel
        ) {
            expenses.filter { expense ->
                val categoryName = categoriesById[expense.categoryId]
                    ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
                    ?: unknownCategoryLabel
                includeExpense(expense) && includeCategory(categoryName)
            }
        }

        val groupedExpenses = remember(
            filteredExpenses,
            categoriesById,
            groupingMode,
            resolveCategoryName,
            unknownCategoryLabel,
            shortMonthNames
        ) {
            val resolveExpenseCategoryLabel: (Expense) -> String = { expense ->
                categoriesById[expense.categoryId]
                    ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
                    ?: unknownCategoryLabel
            }
            val expenseComparator =
                compareByDescending<Expense> { it.date }
                    .thenBy(resolveExpenseCategoryLabel)
                    .thenBy { it.description ?: "" }
            when (groupingMode) {
                ExpenseGroupingMode.ByCategory -> {
                    filteredExpenses
                        .groupBy { expense ->
                            resolveExpenseCategoryLabel(expense)
                        }
                        .toList()
                        .sortedBy { it.first }
                        .map { (groupKey, groupExpenses) ->
                            val sortedExpenses = groupExpenses.sortedWith(expenseComparator)
                            groupKey to sortedExpenses
                        }
                }
                ExpenseGroupingMode.ByDate -> {
                    filteredExpenses
                        .groupBy { expense -> epochMillisToLocalDate(expense.date) }
                        .toList()
                        .sortedByDescending { (_, groupExpenses) ->
                            groupExpenses.maxOf { it.date }
                        }
                        .map { (groupDate, groupExpenses) ->
                            val sortedExpenses = groupExpenses.sortedWith(expenseComparator)
                            formatDateGroupTitle(groupDate, shortMonthNames) to sortedExpenses
                        }
                }
            }
        }
        val totalAmount = remember(groupedExpenses) {
            groupedExpenses.sumBigIntegerOf { (_, expenses) ->
                expenses.sumBigIntegerOf(Expense::amount)
            }
        }
        val deleteExpenseAction: ((String) -> Unit)? = if (canDeleteExpense()) {
            deleteAction@{ expenseId ->
                val expense = filteredExpenses.find { it.id == expenseId } ?: return@deleteAction
                if (expense.recurringSeriesId.isNullOrBlank()) {
                    expenseToDelete = expense
                } else {
                    recurringExpenseToDelete = expense
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
                            if (navigationDescriptor != null) {
                                MonthNavigationTitle(
                                    selectedMonth = selectedMonth,
                                    subtitle = "$navigationDescriptor • ${formatAmount(totalAmount, currencySymbol)}",
                                    onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                    onNextMonth = { selectedMonth = selectedMonth.next() }
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(screenTitle(monthName(selectedMonth.month, fullMonthNames)))
                                    Text(
                                        text = formatAmount(totalAmount, currencySymbol),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(backLabel)
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
                                contentDescription = addExpenseLabel
                            )
                        }
                    }
                }
            ) { padding ->
                GroupedExpensesContent(
                    groupedExpenses = groupedExpenses,
                    categoriesById = categoriesById,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    groupingMode = groupingMode,
                    onGroupingModeChange = { groupingMode = it },
                    onOpenExpense = onOpenExpense,
                    onDeleteExpense = deleteExpenseAction,
                    emptyStateText = emptyStateText,
                    expenseFallbackTitle = expenseFallbackTitle,
                    currencySymbol = currencySymbol,
                    unknownCategoryLabel = unknownCategoryLabel,
                    resolveCategoryName = { category ->
                        resolveCategoryName(category.id, category.name, category.isCustom)
                    },
                    byCategoryLabel = byCategoryLabel,
                    byDateLabel = byDateLabel
                )
            }
        } else {
            GroupedExpensesContent(
                groupedExpenses = groupedExpenses,
                categoriesById = categoriesById,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                groupingMode = groupingMode,
                onGroupingModeChange = { groupingMode = it },
                onOpenExpense = onOpenExpense,
                onDeleteExpense = deleteExpenseAction,
                emptyStateText = emptyStateText,
                expenseFallbackTitle = expenseFallbackTitle,
                currencySymbol = currencySymbol,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name, category.isCustom)
                },
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel
            )
        }

        expenseToDelete?.let { expense ->
            val expenseDisplayName = groupedExpenseRowPresentation(
                expense = expense,
                categoriesById = categoriesById,
                isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                expenseFallbackTitle = expenseFallbackTitle,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name, category.isCustom)
                }
            ).title
            DeleteConfirmationDialog(
                message = deleteItemConfirmationMessageTemplate.formatResourceArgs(expenseDisplayName),
                onDelete = {
                    expenseToDelete = null
                    scope.launch {
                        repository.deleteExpense(expense.id)
                    }
                },
                onDismiss = {
                    expenseToDelete = null
                }
            )
        }

        recurringExpenseToDelete?.let { expense ->
            val expenseDisplayName = groupedExpenseRowPresentation(
                expense = expense,
                categoriesById = categoriesById,
                isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                expenseFallbackTitle = expenseFallbackTitle,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name, category.isCustom)
                }
            ).title
            RecurringSeriesActionDialog(
                title = deleteLabel,
                message = recurringExpenseDeleteMessageTemplate.formatResourceArgs(expenseDisplayName),
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
        groupedExpenses: List<Pair<String, List<Expense>>>,
        categoriesById: Map<String, Category>,
        modifier: Modifier,
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        onOpenExpense: (String) -> Unit,
        onDeleteExpense: ((String) -> Unit)?,
        emptyStateText: String,
        expenseFallbackTitle: String,
        currencySymbol: String,
        unknownCategoryLabel: String,
        resolveCategoryName: (Category) -> String,
        byCategoryLabel: String,
        byDateLabel: String
    ) {
        Box(modifier = modifier) {
            val listModifier = Modifier
                .fillMaxSize()
                .padding(bottom = 84.dp)

            GroupedExpensesList(
                groupedExpenses = groupedExpenses,
                categoriesById = categoriesById,
                groupingMode = groupingMode,
                modifier = listModifier,
                onOpenExpense = onOpenExpense,
                onDeleteExpense = onDeleteExpense,
                emptyStateText = emptyStateText,
                expenseFallbackTitle = expenseFallbackTitle,
                currencySymbol = currencySymbol,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = resolveCategoryName
            )

            GroupingModeButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel,
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
        onDeleteExpense: ((String) -> Unit)?,
        emptyStateText: String,
        expenseFallbackTitle: String,
        currencySymbol: String,
        unknownCategoryLabel: String,
        resolveCategoryName: (Category) -> String
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
                            text = emptyStateText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                return@LazyColumn
            }

            groupedExpenses.forEach { (categoryName, categoryExpenses) ->
                item(key = categoryName) {
                    val expanded = expandedState[categoryName] ?: groupsExpandedByDefault()
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "GroupedExpensesSectionChevronRotation"
                    )
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sectionIconKey = if (groupingMode == ExpenseGroupingMode.ByCategory) {
                                    categoryExpenses.firstOrNull()
                                        ?.categoryId
                                        ?.let(categoriesById::get)
                                        ?.icon
                                } else {
                                    null
                                }

                                CategoryLabel(
                                    iconKey = sectionIconKey,
                                    showIcon = groupingMode == ExpenseGroupingMode.ByCategory,
                                    colorKey = if (groupingMode == ExpenseGroupingMode.ByCategory) {
                                        categoryExpenses.firstOrNull()?.categoryId
                                    } else {
                                        null
                                    },
                                    text = categoryName,
                                    modifier = Modifier.weight(1f),
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatAmount(categoryExpenses.sumBigIntegerOf(Expense::amount), currencySymbol),
                                        textAlign = TextAlign.End
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ExpandableSectionChevron(rotation = chevronRotation)
                                }
                            }
                            if (expanded) {
                                HorizontalDivider()
                                categoryExpenses.forEach { expense ->
                                    key(expense.id) {
                                        val row = groupedExpenseRowPresentation(
                                            expense = expense,
                                            categoriesById = categoriesById,
                                            isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                                            expenseFallbackTitle = expenseFallbackTitle,
                                            unknownCategoryLabel = unknownCategoryLabel,
                                            resolveCategoryName = resolveCategoryName
                                        )

                                        if (onDeleteExpense == null) {
                                            ExpenseListItemRow(
                                                title = row.title,
                                                subtitleText = row.subtitleText,
                                                amountText = formatAmount(expense.amount, currencySymbol),
                                                categoryColorKey = row.categoryColorKey,
                                                categoryIconKey = row.categoryIconKey,
                                                isRecurring = row.isRecurring,
                                                onClick = {
                                                    onOpenExpense(expense.id)
                                                }
                                            )
                                        } else {
                                            val dismissState = rememberExpenseSwipeToDeleteBoxState(
                                                itemId = expense.id,
                                                onDeleteExpense = onDeleteExpense
                                            )

                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = false,
                                                backgroundContent = {
                                                    DeleteExpenseBackground()
                                                }
                                            ) {
                                                ExpenseListItemRow(
                                                    title = row.title,
                                                    subtitleText = row.subtitleText,
                                                    amountText = formatAmount(expense.amount, currencySymbol),
                                                    categoryColorKey = row.categoryColorKey,
                                                    categoryIconKey = row.categoryIconKey,
                                                    isRecurring = row.isRecurring,
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

    protected fun formatDateGroupTitle(
        date: kotlinx.datetime.LocalDate,
        shortMonthNames: List<String>
    ): String {
        return formatExpenseDateGroupTitle(date, shortMonthNames)
    }

    @Composable
    private fun GroupingModeButtons(
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        byCategoryLabel: String,
        byDateLabel: String,
        modifier: Modifier = Modifier
    ) {
        if (!rememberIsIosPlatform()) {
            AndroidGroupingModeSegmentedButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel,
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
                label = byCategoryLabel,
                selected = groupingMode == ExpenseGroupingMode.ByCategory,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByCategory) }
            )
            GroupingModeButton(
                label = byDateLabel,
                selected = groupingMode == ExpenseGroupingMode.ByDate,
                onClick = { onGroupingModeChange(ExpenseGroupingMode.ByDate) }
            )
        }
    }

    @Composable
    private fun AndroidGroupingModeSegmentedButtons(
        groupingMode: ExpenseGroupingMode,
        onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
        byCategoryLabel: String,
        byDateLabel: String,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidGroupingModeButton(
                    label = byCategoryLabel,
                    selected = groupingMode == ExpenseGroupingMode.ByCategory,
                    onClick = { onGroupingModeChange(ExpenseGroupingMode.ByCategory) }
                )
                AndroidGroupingModeButton(
                    label = byDateLabel,
                    selected = groupingMode == ExpenseGroupingMode.ByDate,
                    onClick = { onGroupingModeChange(ExpenseGroupingMode.ByDate) }
                )
            }
        }
    }

    @Composable
    private fun AndroidGroupingModeButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        if (selected) {
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label)
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

    protected fun monthName(month: Int, fullMonthNames: List<String>): String {
        return fullMonthNames[month - 1]
    }
}
